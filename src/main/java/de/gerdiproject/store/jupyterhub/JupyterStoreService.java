package de.gerdiproject.store.jupyterhub;

import de.gerdiproject.store.AbstractStoreService;
import de.gerdiproject.store.datamodel.CopyStatus;
import de.gerdiproject.store.datamodel.ICredentials;
import de.gerdiproject.store.datamodel.ListElement;
import de.gerdiproject.store.datamodel.ResearchDataInputStream;
import de.gerdiproject.store.jupyterhub.models.JHubPersistentVolumeClaim;
import de.gerdiproject.store.jupyterhub.models.JupyterCredentials;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * This represents the logic for the backend of the Jupyter Hub Store service
 */
public class JupyterStoreService extends AbstractStoreService<JupyterCredentials> {

    private static final Logger LOGGER = LoggerFactory
            .getLogger(JupyterStoreService.class);

    private final CoreV1Api k8sApi;

    public JupyterStoreService() throws IOException {
        super();
        ApiClient k8sClient = Config.defaultClient();
        Configuration.setDefaultApiClient(k8sClient);
        k8sApi = new CoreV1Api();
    }

    /**
     * This gets this program goin'
     *
     * @param args Command line arguments - not used here
     */
    public static void main(final String[] args) throws IOException {
        final JupyterStoreService service = new JupyterStoreService();
        service.run();
    }

    @Override
    protected boolean isLoggedIn(final JupyterCredentials creds) {
        return creds != null;
    }

    @Override
    protected JupyterCredentials login(final Request req, final Response res) {
        String username = req.cookie("username");
        boolean wait = req.queryParams("wait") != null;
        LOGGER.info("Wait set to " + wait);
        if (username == null) {
            res.status(400);
            return null;
        }
        username = username.toLowerCase(); // K8S only supports lowercase
        File target = null;
        int counter = 0;
        while (target == null && (counter < 4 || wait)) {
            try {
                target = getPersistentVolumeClaim(username);
            } catch (ApiException e) {
                LOGGER.error("Could not connect to Kubernetes API Server.", e);
                res.status(500);
                return null;
            }

            if (target == null && counter == 0) {
                try {
                    createPersistentVolumeClaim(username);
                } catch (ApiException e) {
                    LOGGER.error("Could not create PersistentVolumeClaim.", e);
                    res.status(500);
                    return null;
                }
            }
            if (target == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    LOGGER.error("Sleep interrupted", e);
                }
            }
            counter++;
        }
        if (target != null) {
            LOGGER.info("Target " + target.getPath());
        }
        JupyterCredentials creds = new JupyterCredentials();
        creds.setUsername(username);
        creds.setPersistentVolumePath(target);

        if (target == null) creds = null;

        return creds;
    }

    @Override
    protected boolean copyFile(final JupyterCredentials creds, final String targetDir, final ResearchDataInputStream taskElement) {
        int lastIndex = taskElement.getName().lastIndexOf("/");
        String targetFileName = lastIndex != 0 ? taskElement.getName().substring(lastIndex) : taskElement.getName();
        File target = new File(creds.getPersistentVolumePath().getAbsolutePath() + targetDir + targetFileName);
        if (target.exists()) {
            taskElement.setStatus(CopyStatus.ERROR);
        }
        new Thread(() -> {
            try {
                Files.copy(taskElement, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                taskElement.setStatus(CopyStatus.FINISHED);
            } catch (IOException e) {
                LOGGER.error("Could not copy data.", e);
                taskElement.setStatus(CopyStatus.ERROR);
            }
        }).start();
        return true;
    }

    @Override
    protected List<ListElement> listFiles(final String directory, final JupyterCredentials creds) {
        List<ListElement> retVal = new ArrayList<>();
        File path = new File(creds.getPersistentVolumePath().getAbsolutePath() + directory);
        LOGGER.info("Retrieving files from " + path.getAbsolutePath());
        if (path.listFiles() == null) LOGGER.error("Path returns null list. " + path.getAbsolutePath());
        for (File it: path.listFiles()) {
            String type;
            try {
                type = it.isDirectory() ? "httpd/unix-directory" : Files.probeContentType(it.toPath());
            } catch (IOException e) {
                LOGGER.error("Unable to retrieve file type", e);
                type = "application/octet-stream";
            }

            retVal.add(ListElement.of(it.getName(), type, it.getAbsolutePath().replace(creds.getPersistentVolumePath().getAbsolutePath(), "")));
        }
        return retVal;
    }

    @Override
    protected boolean createDir(String dir, final String dirName, final JupyterCredentials creds) {
        final File mountPath = creds.getPersistentVolumePath();
        if (dir.endsWith("/")) dir = dir.substring(0, dir.length()-1);
        return new File(mountPath + dir + "/" + dirName).mkdir();
    }

    /**
     * Retrieves the directory used for the Kubernetes PersistentVolumeClaim
     *
     * @param username The username linked to the PersistentVolumeClaim
     * @return A File instance pointing to the directory or null if volume does not exist.
     */
    private final File getPersistentVolumeClaim(String username) throws ApiException {
        File retVal = null;
        V1PersistentVolumeClaimList list = k8sApi.listNamespacedPersistentVolumeClaim("jhub",null, null, null,null,null,null,null,null,null);
        for (V1PersistentVolumeClaim item : list.getItems()) {
            final String claimUsername = item.getMetadata().getAnnotations().get("hub.jupyter.org/username");
            if (claimUsername != null && claimUsername.equals(username)) {
                String volumeName = item.getSpec().getVolumeName();
                if (volumeName == null) break;
                retVal = new File("/mnt/nfs/nfs-test/jhub-claim-" + username + "-" + volumeName);
                break;
            }
        }
        return retVal;
    }

    /**
     * Creates a PersistentVolumeClaim in Kubernetes for a given user
     *
     * @param username The user for whom the Claim should be created
     * @throws ApiException
     */
    private final void createPersistentVolumeClaim(String username) throws ApiException {
        V1PersistentVolumeClaim createdClaim = k8sApi.createNamespacedPersistentVolumeClaim("jhub",new JHubPersistentVolumeClaim(username), null);
    }

}

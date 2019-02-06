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
        if (username == null) {
            res.status(400);
            return null;
        }
        File target = null;
        try {
            target = getPersistentVolumeClaim(username);
        } catch (ApiException e) {
            LOGGER.error("Could not connect to Kubernetes API Server.", e);
            res.status(500);
            return null;
        }

        if (target == null) {
            try {
                target = createPersistentVolumeClaim(username);
            } catch (ApiException e) {
                LOGGER.error("Could not create PersitentVolumeClaim.", e);
                res.status(500);
                return null;
            }
        }
        JupyterCredentials creds = new JupyterCredentials();
        creds.setUsername(username);
        creds.setPersistentVolumePath(target);

        return creds;
    }

    @Override
    protected boolean copyFile(final JupyterCredentials creds, final String targetDir, final ResearchDataInputStream taskElement) {
        File target = new File(creds.getPersistentVolumePath().getAbsolutePath() + targetDir + taskElement.getName());
        if (target.exists()) {
            taskElement.setStatus(CopyStatus.ERROR);
        }
        new Thread(() -> {
            try {
                Files.copy(taskElement, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
        // TODO: Construct correct Path
        File path = new File(creds.getPersistentVolumePath().getAbsolutePath() + directory);
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
        return null;
    }

    /**
     *
     * @param username
     * @return or null if volume does not exist.
     */
    private final File getPersistentVolumeClaim(String username) throws ApiException {
        File retVal = null;
        V1PersistentVolumeClaimList list = k8sApi.listNamespacedPersistentVolumeClaim("jhub",null, null, null,null,null,null,null,null,null);
        for (V1PersistentVolumeClaim item : list.getItems()) {
            final String claimUsername = item.getMetadata().getAnnotations().get("hub.jupyter.org/username");
            if (claimUsername.equals(username)) {
                String volumeName = item.getSpec().getVolumeName();
                retVal = new File("/mnt/nfs/nfs-test/jhub-claim-" + username + "-" + volumeName);
                break;
            }
        }
        return retVal;
    }

    private final File createPersistentVolumeClaim(String username) throws ApiException {
        k8sApi.createNamespacedPersistentVolumeClaim("jhub",new JHubPersistentVolumeClaim(username), null);
        return null;
    }

}

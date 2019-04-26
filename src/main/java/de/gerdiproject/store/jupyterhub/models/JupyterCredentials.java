package de.gerdiproject.store.jupyterhub.models;

import de.gerdiproject.store.datamodel.ICredentials;
import lombok.Data;
import org.apache.commons.lang.RandomStringUtils;

import java.io.File;

/**
 * This represents the credentials needed to provide this service
 */
public  @Data
class JupyterCredentials implements ICredentials {

    private String username;
    private File persistentVolumePath;
    private final String pseudoName = RandomStringUtils.random(15);

}

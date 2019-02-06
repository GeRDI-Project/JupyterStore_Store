package de.gerdiproject.store.jupyterhub.models;

import de.gerdiproject.store.datamodel.ICredentials;
import lombok.Data;
import org.apache.commons.lang.RandomStringUtils;

import java.io.File;

public  @Data
class JupyterCredentials implements ICredentials {

    private String username;
    private File persistentVolumePath;
    private final String pseudoName = RandomStringUtils.random(15);

}

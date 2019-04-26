/**
 * Copyright © 2019 Nelson Tavares de Sousa (tavaresdesousa@email.uni-kiel.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

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

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.models.V1ResourceRequirements;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This represents an abstract PersistentVolumeClaim which can be used to create a Claim compatible with JupyterHub
 *
 * @author Nelson Tavares de Sousa
 */
public class JHubPersistentVolumeClaim extends V1PersistentVolumeClaim {

    /**
     * Constructor to create the Claim
     * @param username The user's name
     */
    public JHubPersistentVolumeClaim(String username) {
        this.setMetadata(this.createMetadata(username));
        this.setSpec(this.createSpec());
        this.setApiVersion("v1");
    }

    private static final V1ObjectMeta createMetadata(String username) {
        final V1ObjectMeta retVal = new V1ObjectMeta();

        final Map<String, String> annotations = new HashMap<>();
        annotations.put("hub.jupyter.org/username", username);

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "jupyterhub");
        labels.put("chart", "jupyterhub-0.7.0");
        labels.put("component", "singleuser-storage");
        labels.put("heritage", "jupyterhub");
        labels.put("release", "jhub");

        retVal.setAnnotations(annotations);
        retVal.setLabels(labels);
        retVal.setName("claim-" + username);
        retVal.setNamespace("jhub");

        return retVal;
    }

    private static final V1PersistentVolumeClaimSpec createSpec() {
        final V1PersistentVolumeClaimSpec retVal = new V1PersistentVolumeClaimSpec();

        final V1ResourceRequirements requirements = new V1ResourceRequirements();
        final Map<String, Quantity> storageRequest = new HashMap<>();
        storageRequest.put("storage", new Quantity("1Gi"));
        requirements.setRequests(storageRequest);

        retVal.setResources(requirements);
        retVal.setAccessModes(Arrays.asList("ReadWriteOnce"));
        retVal.setStorageClassName("managed-nfs-storage");

        return retVal;
    }

}

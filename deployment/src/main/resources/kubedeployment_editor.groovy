/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

@Grapes(
        @Grab(group='org.yaml', module='snakeyaml', version='1.24')
)
@Grapes(
        @Grab(group='org.json', module='json', version='20180813')
)
import org.yaml.snakeyaml.Yaml
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener


/**
 * Adds a new Items to an Existing Property
 * @param variable - Map in which existing property must be changed
 *
 */
def AddNewItem(Map variable, String propertyName , Object new_Value){
    try{
        ArrayList prev_property = (ArrayList)variable.get(propertyName)
        prev_property.add(new_Value)
        variable.put(propertyName,prev_property)
        return variable
    }catch(RuntimeException e){
        println(e)
    }
}

/**
 * Formats the user provided inputs to a form that can be utilized by the sidecar container and log extraction
 * container
 *
 * @param loglocations JSONArray containing the log locations
 * @param i index of the required JSONobject in the array
 * @return
 */

def Formattedfilepaths(JSONArray loglocations, Integer i){

    String containerFilepath
    String sidecarFilepath
    if( loglocations.getJSONObject(i).get("path").toString().startsWith('/')
            && loglocations.getJSONObject(i).get("path").toString().endsWith('/') ){
        containerFilepath = loglocations.getJSONObject(i).get("path")
        sidecarFilepath = ("/opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"+
                loglocations.getJSONObject(i).get("containername")+loglocations.getJSONObject(i).get("path"))
    }else if( loglocations.getJSONObject(i).get("path").toString().startsWith('/')
            && !loglocations.getJSONObject(i).get("path").toString().endsWith('/') ){
        containerFilepath = loglocations.getJSONObject(i).get("path")+"/"
        sidecarFilepath = ("/opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"+
                loglocations.getJSONObject(i).get("containername")+loglocations.getJSONObject(i).get("path")+"/")
    }else if( !loglocations.getJSONObject(i).get("path").toString().startsWith('/')
            && loglocations.getJSONObject(i).get("path").toString().endsWith('/') ){
        containerFilepath = "/"+loglocations.getJSONObject(i).get("path")
        sidecarFilepath = ("/opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"+
                loglocations.getJSONObject(i).get("containername")+"/"+loglocations.getJSONObject(i).get("path"))
    }else{
        containerFilepath = "/"+loglocations.getJSONObject(i).get("path")+"/"
        sidecarFilepath = ("/opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"+
                loglocations.getJSONObject(i).get("containername")+"/"+loglocations.getJSONObject(i).get("path")+"/")
    }
    return [ containerFilepath, sidecarFilepath ]

}


/**
 * Adds Mount path and Sidecar container to the Deployment
 * @param outputYaml - file name of the edited yaml file
 * @param jsonFilePath - file path to the json file containing input Parameters
 * @param pathToDeployment - file path to the input Yaml file
 *
 */
def EditK8SDeployments(String depType, String productName, String outputYaml,String jsonFilePath, String pathToDeployment){
    try{
        // Read json file
        InputStream is = new FileInputStream(jsonFilePath.toString())
        JSONTokener tokener = new JSONTokener(is)
        JSONObject json = new JSONObject(tokener)
        JSONArray loglocations;
        if ( depType.equals("HELM") ) {
            loglocations = json.getJSONObject("dep-in").getJSONObject(productName).getJSONArray("LogFileLocations")
        } else if (depType.equals("K8S")) {
            loglocations = json.getJSONObject("dep-in").getJSONArray("LogFileLocations")
        }
        Yaml yaml = new Yaml()
        InputStream inputStream = new FileInputStream(pathToDeployment)

        Iterable<Object> KubeGroups = yaml.loadAll(inputStream)
        FileWriter fileWriter = new FileWriter(outputYaml)

        if (loglocations.length() != 0){
            for (Object KubeGroup : KubeGroups ) {
                Map<String, Object> group = (Map<String, Object>) KubeGroup
                int logcontainers = 0

                // If group is empty skip
                if(group.is(null))break

                // Only consider Deployment related yaml files
                if(group.get("kind").equals("Deployment")){

                    // Get Deployment Metadata
                    Map<String, Object> depmeta = (Map<String, Object>) group.get("metadata")

                    Boolean hasSidecarbeenAdded = false;

                    // Volume Mounts for the sidecar container
                    List SidecarVolMounts = []

                    // List of updated containers with a volume mounted at the log file location
                    ArrayList newcontainerlist = []

                    // String that must be run while initializing the sidecar container
                    String CommandString = ""


                        for ( Map container in group.get("spec").get("template").get("spec").get("containers")){

                            int i = 0
                            boolean matchfound = false
                            // For each container check if a log file location has been provided in the json file
                            for (; i < loglocations.length(); i++) {
                                JSONObject temp = loglocations.getJSONObject(i)
                                // When found break the loop
                                if(temp.getString("deploymentname").equals( depmeta.get("name") )
                                        && temp.getString("containername").equals(container.get("name"))) {
                                    hasSidecarbeenAdded = true
                                    matchfound = true
                                    break
                                }
                            }
                            // If a match is found enter the updated container into the list
                            if (matchfound){

                                List formattedPaths = Formattedfilepaths(loglocations,i)
                                String containerFilepath = formattedPaths[0]
                                String sidecarFilepath = formattedPaths[1]

                                // New volume mount for the container
                                // new volume mount for the sidecar container
                                // Echo the path into a file in the sidecar container so it knows that it is a log path
                                Map new_VolumeMount =
                                        ["name":"logfilesmount"+logcontainers, "mountPath": containerFilepath]
                                SidecarVolMounts
                                        .add(["name":"logfilesmount"+logcontainers, "mountPath": sidecarFilepath])
                                CommandString = ( CommandString + "echo executearchive "+ sidecarFilepath +
                                        " logfilesmount"+ logcontainers+ " " +
                                        loglocations.getJSONObject(i).get("deploymentname") + " " +
                                        loglocations.getJSONObject(i).get("containername") +" >> log_archiver.sh &&"
                                )

                                newcontainerlist.add(AddNewItem(container,"volumeMounts",new_VolumeMount))
                                logcontainers++
                            }else{
                                //Add container as it is
                                newcontainerlist.add(container)
                            }
                        }


                    group.get("spec").get("template").get("spec").put("containers", newcontainerlist)

                    if(hasSidecarbeenAdded){
                        // Add the updated container list as the deployment yamls container list

                        /*
                        Change to persistent disk claim if using persistent disk
                         */
                        Map emptyMap = [:]
                        for (int j = 0; j<logcontainers;j++){
                            Map new_Volume = ["name": "logfilesmount"+j , "emptyDir": emptyMap ]
                            group.get("spec").get("template").put("spec",AddNewItem(
                                    group.get("spec").get("template").get("spec"),"volumes",new_Volume))
                        }

                        JSONObject currentscriptParams = json.getJSONObject("dep-in")
                        /*
                         Remove entirely if using persistent disk
                        */
                        CommandString = CommandString + " echo transfer >> log_archiver.sh && "
                        Map new_Container =
                                [ "name": "logfile-sidecar" ,
                                  "image":"ranikamadurawe/mytag",
                                  "volumeMounts":SidecarVolMounts,
                                  "env": [
                                          ["name": "nodename" ,
                                           "valueFrom" : ["fieldRef" : ["fieldPath" : "spec.nodeName"]]],
                                          ["name": "podname" ,
                                           "valueFrom" : ["fieldRef" : ["fieldPath" : "metadata.name"]]],
                                          ["name": "podnamespace" ,
                                           "valueFrom" : ["fieldRef" : ["fieldPath" : "metadata.namespace"]]],
                                          ["name": "podip" ,
                                           "valueFrom" : ["fieldRef" : ["fieldPath" : "status.podIP"]]],
                                          ["name": "wsEndpoint" ,
                                           "value": currentscriptParams.getString("DEPLOYMENT_TINKERER_EP")],
                                          ["name": "region" , "value": "US"  ],
                                          ["name": "provider" , "value": "K8S" ],
                                          ["name": "testPlanId" ,
                                           "value": currentscriptParams.getString("tpID")  ],
                                          ["name": "userName" ,
                                           "value": currentscriptParams.getString("DEPLOYMENT_TINKERER_USERNAME")],
                                          ["name": "password" ,
                                           "value": currentscriptParams.getString("DEPLOYMENT_TINKERER_PASSWORD")],
                                  ],
                                  "command": ["/bin/bash", "-c",
                                              CommandString+"./kubernetes_startup.sh && tail -f /dev/null" ]
                                ]
                        group.get("spec").get("template").put("spec",AddNewItem(
                                group.get("spec").get("template").get("spec"),"containers",new_Container))
                    }

                }
                yaml.dump(group,fileWriter)
                fileWriter.write("---\n\n")

            }
        }else{
            println("no log file paths provided in parameter")
        }

        fileWriter.close()

    }catch(RuntimeException e){
        println(e)
    }
}

/**
 * Args must be provided in following order
 * outputyaml_name   path_to_testLogs   path_to_Deployment.yaml
 */
EditK8SDeployments(args[0],args[1],args[2],args[3],args[4])

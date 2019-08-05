/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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


/*
Adds a new Items to an Existing Property
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

/*
This method reads the config.properties file
 */
def static readconfigProperties(){

    InputStream testplanInput = new FileInputStream("tpid.properties")
    InputStream configinput = new FileInputStream("/opt/testgrid/home/config.properties")
    Properties config = new Properties()
    Properties tpid = new Properties()
    config.load(configinput)
    tpid.load(testplanInput)
    config.setProperty("TESTPLANID",tpid.getProperty("tpID"))

    testplanInput.close()
    return config
}

/*
Adds Mount path and Sidecar container to the Deployment
 */
def EditDeployments(String outputYaml,String jsonFilePath, String pathToDeployment){


    try{

        // Read json file

        InputStream is = new FileInputStream(jsonFilePath.toString())
        JSONTokener tokener = new JSONTokener(is)
        JSONObject json = new JSONObject(tokener)
        JSONArray loglocations = json.getJSONObject("currentscript").getJSONArray("LogFileLocations")
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

                if(group.get("kind").equals("Deployment")){

                    // Get Deployment Metadata
                    Map<String, Object> depmeta = (Map<String, Object>) group.get("metadata")

                    // Volume Mounts for the sidecar container
                    List volumeMounts = []

                    // List of updated containers with a volume mounted at the log file location
                    ArrayList newcontainerlist = []
                    String CommandString = ""

                    for ( Map container in group.get("spec").get("template").get("spec").get("containers")){

                        int i = 0
                        boolean matchfound = false
                        // For each container check if a log file location has been provided in the json file
                        for (; i < loglocations.length(); i++) {
                            JSONObject temp = loglocations.getJSONObject(i)
                            // When found break the loop
                            if(temp.getString("deploymentname").equals( depmeta.get("name") ) && temp.getString("containername").equals(container.get("name"))) {
                                matchfound = true
                                break
                            }
                        }
                        // If a match is found enter the updated container into the list
                        if (matchfound){

                            // New volume mount for the container
                            // new volume mount for the sidecar container
                            if( loglocations.getJSONObject(i).get("path").toString().startsWith('/') ){
                                new_VolumeMount = ["name":"logfilesmount"+logcontainers, "mountPath": loglocations.getJSONObject(i).get("path")]
                                volumeMounts.add(["name":"logfilesmount"+logcontainers, "mountPath":"opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"
                                        +loglocations.getJSONObject(i).get("containername")+loglocations.getJSONObject(i).get("path")])
                                CommandString = CommandString + "echo executearchive opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"+loglocations.getJSONObject(i).get("containername")+loglocations.getJSONObject(i).get("path")+" logfilesmount"+logcontainers
                                                  +" >> logarchiver.sh && "
                            }else{
                                new_VolumeMount = ["name":"logfilesmount"+logcontainers, "mountPath": "/"+loglocations.getJSONObject(i).get("path")]
                                volumeMounts.add(["name":"logfilesmount"+logcontainers, "mountPath":"opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"
                                        +loglocations.getJSONObject(i).get("containername")+"/"+loglocations.getJSONObject(i).get("path")])
                                CommandString = CommandString + "echo executearchive opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/" +loglocations.getJSONObject(i).get("containername")+"/"+loglocations.getJSONObject(i).get("path")+" logfilesmount"+logcontainers
                                +" >> logarchiver.sh && "
                            }
                            newcontainerlist.add(AddNewItem(container,"volumeMounts",new_VolumeMount))
                            logcontainers++
                        }else{
                            // If not found add container with default path

                            // New volume mount for the container
                            new_VolumeMount = ["name":"logfilesmount"+logcontainers, "mountPath": "/opt/testgrid/logfilepath"]
                            // new volume mount for the sidecar container
                            volumeMounts.add(["name":"logfilesmount"+logcontainers, "mountPath":"opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/"
                                    +loglocations.getJSONObject(i).get("containername")+"/opt/testgrid/logfilepath"])
                            CommandString = CommandString + "echo executearchive opt/tests/"+loglocations.getJSONObject(i).get("deploymentname")+"/" +loglocations.getJSONObject(i).get("containername")+"/opt/testgrid/logfilepath logfilesmount"+logcontainers
                            +" > logarchiver.sh && "
                            newcontainerlist.add(AddNewItem(container,"volumeMounts",new_VolumeMount))
                            logcontainers++
                        }
                    }

                    // Add the updated container list as the deployment yamls container list
                    group.get("spec").get("template").get("spec").put("containers", newcontainerlist)

                    /*
                    Change to persistent disk claim if using persistent disk
                     */
                    Map emptyMap = [:]
                    for (int j = 0; j<logcontainers;j++){
                        Map new_Volume = ["name": "logfilesmount"+j , "emptyDir": emptyMap ]
                        group.get("spec").get("template").put("spec",AddNewItem(group.get("spec").get("template").get("spec"),"volumes",new_Volume))
                    }

                    /*
                   Remove entirely if using persistent disk
                    */

                    Properties configprops = readconfigProperties()
                    CommandString = CommandString + " echo transfer >> logarchiver.sh && "
                    Map new_Container = [ "name": "logfile-sidecar" , "image":"ranikamadurawe/mytag", "volumeMounts":volumeMounts,
                                          "env": [ ["name": "nodename" , "valueFrom" : ["fieldRef" : ["fieldPath" : "spec.nodeName"]] ],
                                                   ["name": "podname" , "valueFrom" : ["fieldRef" : ["fieldPath" : "metadata.name"]] ],
                                                   ["name": "podnamespace" , "valueFrom" : ["fieldRef" : ["fieldPath" : "spec.metadata.namespace"]] ],
                                                   ["name": "podip" , "valueFrom" : ["fieldRef" : ["fieldPath" : "status.podIP"]] ],
                                                   ["name": "wsEndpoint" , "value": configprops.getProperty("DEPLOYMENT_TINKERER_EP") ],
                                                   /* --NEED TO CHANGE -- */                ["name": "region" , "value": "US"  ],
                                                   ["name": "provider" , "value": "K8S" ],
                                                   ["name": "testPlanId" , "value": configprops.getProperty("TESTPLANID")  ],
                                                   ["name": "userName" , "value": configprops.getProperty("DEPLOYMENT_TINKERER_USERNAME") ],
                                                   ["name": "password" , "value": configprops.getProperty("DEPLOYMENT_TINKERER_PASSWORD") ],

                                          ],
                                          "command": ["/bin/bash", "-c", CommandString+"./kubernetes_startup.sh && tail -f /dev/null" ]
                    ]
                    group.get("spec").get("template").put("spec",AddNewItem(group.get("spec").get("template").get("spec"),"containers",new_Container))

                }
                yaml.dump(group,fileWriter)
                fileWriter.write("---\n\n")

            }
        }else{
            println("no log file paths provided in parameter")
        }

        fileWriter.close()
        new File("tpid.properties").delete()
    }catch(RuntimeException e){
        println(e)
    }
}

/*
Args must be provided as --outputyaml_name --path_to_testLogs --path_to_Deployment.yaml
 */
EditDeployments(args[0],args[1],args[2])

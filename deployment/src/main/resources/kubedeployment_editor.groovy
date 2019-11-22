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
 * Formats the user provided inputs to a form that can be utilized by the sidecar container and log extraction
 * container
 *
 * @param loglocations JSONArray containing the log locations
 * @param i index of the required JSONobject in the array
 * @return
 */

def formatFilePaths(String loglocations){

    String containerFilepath
    if( loglocations.startsWith('/') && loglocations.endsWith('/') ){
        containerFilepath = loglocations
    }else if( loglocations.startsWith('/') && !loglocations.endsWith('/') ){
        containerFilepath = loglocations+"/"
    }else if( !loglocations.startsWith('/') && loglocations.endsWith('/') ){
        containerFilepath = "/"+loglocations
    }else{
        containerFilepath = "/"+loglocations+"/"
    }
    return containerFilepath

}

/**
 * Returns the appropriate conf file which is to become the logstash.conf of the Logstash-collector-deployment
 *
 * @param logOptions - Log Options of the job
 * @return
 */
def deriveConfFile(JSONObject logOptions){
    return "default.conf"
}


/**
 * Creates yaml file which store all details required for sidecar injector to inject appropriate information to
 * the deployment
 *
 * @param logPathDetailsYamlLoc - Path to yaml file which is to store Log Path details
 * @param depInJSONFilePath - File path to the json file containing input Parameters
 * @param depType - Type of the deployment ( Helm or K8S )
 * @param esEndPoint - Elastic Search Endpoint
 * @param depRepo - Path to the deployment Repository
 *
 */
def confLogCapabilities(String logPathDetailsYamlLoc, String depInJSONFilePath, String depType ){
    try{
        // Read json file
        InputStream depInJSONinputStream = new FileInputStream(depInJSONFilePath.toString())
        JSONTokener tokener = new JSONTokener(depInJSONinputStream)
        JSONObject depInJSON = new JSONObject(tokener)

        JSONObject logOptions = depInJSON.getJSONObject("dep-in").getJSONObject("log-Options");
        String esEndpoint = depInJSON.getJSONObject("dep-in").getString("esEP")
        String depRepo = depInJSON.getJSONObject("dep-in").getString("depRepoLoc")

        String logRequirment = logOptions.getString("logRequirement")

        if (logRequirment.equals("Sidecar-Required")) {
            // If sidecar is required create logpathdetails file with all infomation
            JSONArray loglocations = logOptions.getJSONArray("logFileLocations")
            Yaml yaml = new Yaml()

            FileWriter fileWriter = new FileWriter(logPathDetailsYamlLoc)

            if (loglocations.length() != 0){
                List logpathConf = []
                for (JSONObject logLocation in loglocations) {
                    String formatFilePath = formatFilePaths(logLocation.getString("path"))
                    Map entry = ["name" : logLocation.getString("deploymentname") + "-" +
                            logLocation.getString("containername") , "path" : formatFilePath]
                    logpathConf.add( entry )
                }
                String logConfFile = deriveConfFile(logOptions)
                Map logconf = [ "onlyvars":false, "loglocs" : logpathConf]
                yaml.dump(logconf,fileWriter)
                println("SidecarReq ".concat(logConfFile))
            } else {
                println("False")
            }
            fileWriter.close()
            return
        } else if ( logRequirment.equals("log-endPoints-Required") ) {
            if (depType.equals("helm")) {
                // If only ES endpoint is required access values.yaml file and edit the appropriate value
                String valuesYamlLoc = depRepo.concat("/").concat(depInJSON.getJSONObject("dep-in").
                        getString("rootProjLocations"))
                        .concat("/").concat(logOptions.getString("valuesYamlLocation"));
                JSONArray replacableValues = logOptions.getJSONArray("replaceableVals")
                InputStream valuesYamlInputStream = new FileInputStream(valuesYamlLoc);
                Yaml yaml = new Yaml()
                Map valuesYaml = yaml.load(valuesYamlInputStream)
                String esURL
                if(esEndpoint.startsWith("https://")){
                    esURL = (esEndpoint.split("https://")[1]).split(":")[0]
                }else if(esEndpoint.startsWith("http://")){
                    esURL = (esEndpoint.split("http://")[1]).split(":")[0]
                }
                String esPort = esEndpoint.split(":")[2]

                for ( JSONObject replacableObj in replacableValues ){
                    Map editedMap = valuesYaml
                    String replaceableObjLoc = replacableObj.getString("loc").split(":")[0]
                    List<String> pathToRepObj = replaceableObjLoc.split("/")
                    String key;
                    for (int i = 0 ; i < pathToRepObj.size() -1 ; i++ ) {
                        key = pathToRepObj[i];
                        editedMap = editedMap[key]
                    }
                    // add more ifs for other vars
                    if (replacableObj.getString("type").equals("esEndPoint")) {
                        editedMap[pathToRepObj[pathToRepObj.size()-1]] = esURL
                    } else if (replacableObj.getString("type").equals("esPort")) {
                        editedMap[pathToRepObj[pathToRepObj.size()-1]] = esPort
                    }
                }
                valuesYamlInputStream.close()
                FileWriter valuesYamlOutputStream = new FileWriter(valuesYamlLoc)
                yaml.dump(valuesYaml,valuesYamlOutputStream)
                valuesYamlOutputStream.close();
                println("False")
            } else if ( depType.equals("k8s")) {
                // If k8s inject a env Var to the deployments which stores the elastic search endpoint
                FileWriter fileWriter = new FileWriter(logPathDetailsYamlLoc)
                Yaml yaml = new Yaml()
                List envVars = []
                // Add more ifs for other variables
                if(logOptions.has("esVarName")){
                    envVars.add(["name": logOptions.getString("esVarName"), "value" : esEndpoint])
                }
                if(envVars.size() > 0 ){
                    Map logconf = [ "onlyvars":true , "envvars": envVars ]
                    yaml.dump(logconf,fileWriter)
                    fileWriter.close()
                    println("onlyES null")
                } else {
                    fileWriter.close()
                    println("False")
                }
            }
        } else if ( logRequirment.equals("None") ) {
            println("False")
        }
    }catch(RuntimeException e){
        println(e)
    }
}

/**
 * Args must be provided in following order
 * outputyaml_name   path_to_testLogs   path_to_Deployment.yaml
 */
confLogCapabilities(args[0],args[1],args[2])

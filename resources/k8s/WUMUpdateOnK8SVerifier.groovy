pipeline
{
    agent any

    environment
    {
      FILE_SEC = "key.json"
      WORKSPACE = "docker_build"
      INSTALLMENT="installment"
    }

    stages {
        stage('prepare-workspace')
        {
          steps
          {
          wrap([$class: 'MaskPasswordsBuildWrapper']) { // to enable mask-password plugin

          echo "Build started."
            withCredentials([file(credentialsId: 'GKE_BOT_GCE_SERVICE_ACC', variable: 'keyLocation')])
            {
              sh '''
              if [ -d $WORKSPACE ]; then
                rm -r ${WORKSPACE}
              fi
              mkdir ${WORKSPACE}
              if [ ! -d ${INSTALLMENT} ]; then
                mkdir ${INSTALLMENT}
              else
                echo "directory exists"
                rm -r ${INSTALLMENT}/*
              fi

              cp ${keyLocation} ${INSTALLMENT}/${FILE_SEC}
              chmod 400 ${INSTALLMENT}/${FILE_SEC}
              '''
            }
            }
          }
        }

        /*
        TODO: if location of the WUMUpdateOnK8sVerifier changes
                1. clone the repo
                2. change the directory path of .sh files in the next step
                3. change $BUILD_SCRIPT in build_latest.sh
        */

        stage('run-script')
        {
          steps
          {
          wrap([$class: 'MaskPasswordsBuildWrapper']) { // to enable mask-password plugin
            withCredentials([string(credentialsId: 'GCP_GCR_IMGPULL_USERNAME', variable: 'WUM_USERNAME'),
                             string(credentialsId: 'GCP_GCR_IMGPULL_PASSWORD', variable: 'WUM_PASSWORD'),
                             string(credentialsId: 'ORACLE_ACC_USER', variable: 'ORACLE_USER'),
                             string(credentialsId: 'ORACLE_ACC_PWD', variable: 'ORACLE_PASS'),
                             string(credentialsId: 'GITHUB_ACCESS_TOKEN', variable: 'ACCESS_TOKEN'),
                             string(credentialsId: 'WUM_UAT_URL', variable: 'UAT_URL'),
                             string(credentialsId: 'WUM_UAT_APPKEY', variable: 'UAT_APPKEY')
            ])
            {
              sh """
                ls
                cd ${WORKSPACE}
                chmod +x ../resources/k8s/build_latest.sh
                chmod +x ../resources/k8s/docker_build.sh
                sh ../resources/k8s/build_latest.sh
              """
            }
          }
          }
        }
    }
}

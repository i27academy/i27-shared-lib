import com.i27academy.builds.Calculator
import com.i27academy.builds.Docker
import com.i27academy.k8s.K8s

def call(Map pipelineParams){
    // An instance of the class called calculator is created
    Calculator calculator = new Calculator(this)
    Docker docker = new Docker(this)   
    K8s k8s = new K8s(this) 

// This Jenkinsfile is for Eureka Deployment 

    pipeline {
        agent {
            label 'k8s-slave'
        }
        parameters {
            choice(name: 'dockerPush',
                choices: 'no\nyes',
                description: 'This Will build dockerImage and Push'
            )
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'This Will deploy to Dev'
            )
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'This Will deploy to Test'
            )
            choice(name: 'deployToStage',
                choices: 'no\nyes',
                description: 'This Will deploy to Stage'
            )
            choice(name: 'deployToProd',
                choices: 'no\nyes',
                description: 'This Will deploy to Prod'
            )
        }

        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            DOCKER_HUB = "docker.io/i27devopsb4"
            DOCKER_CREDS = credentials('dockerhub_creds') //username and password
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TST_FILE = "k8s_tst.yaml"
            K8S_STG_FILE = "k8s_stg.yaml"
            K8S_PRD_FILE = "k8s_prd.yaml"
            DEV_NAMESPACE = "clothing-dev-ns"
            TST_NAMESPACE = "clothing-tst-ns"
            STG_NAMESPACE = "clothing-stg-ns"
            PROD_NAMESPACE = "clothing-prd-ns"
        }
        stages {
            stage ('Authentication'){
                steps {
                    echo "Executing in GCP project"
                    script {
                        k8s.auth_login()
                    }
                }
            }
            stage ('Docker Build and Push') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                        }
                    }
                }
                steps { 
                    script {
                        dockerBuildAndPush().call()
                    }
                } 
            }
            stage ('Deploy to Dev') {
                when {
                    expression {
                        params.deployToDev == 'yes'
                    }
                }
                steps {
                    script {
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                        echo "Deployed to Dev Successfully"
                    }
                }
            }
            stage ('Deployed to Test') {
                when {
                    expression {
                        params.deployToTest == 'yes'
                    }
                }
                steps {
                    script {
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.k8sdeploy("${env.K8S_TST_FILE}", docker_image, "${env.TST_NAMESPACE}")
                        echo "Deployed to Test Successfully"
                    }
                }
            }
            stage ('Deploy to Stage') {
                when {
                    allOf {
                        anyOf {
                            expression {
                                params.deployToStage == 'yes'
                                // other condition
                            }
                        }
                        anyOf{
                            branch 'release/*'
                        }
                    }
                }
                steps {
                    script {
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.k8sdeploy("${env.K8S_STG_FILE}", docker_image, "${env.STG_NAMESPACE}")
                        echo "Deployed to Stage Successfully"
                    }
                }
            }
            stage('Deploy to Prod') {
                when {
                    allOf {
                        anyOf{
                            expression {
                                params.deployToProd == 'yes'
                            }
                        }
                        anyOf{
                            tag pattern: "v\\d{1,2}\\.\\d{1,2}\\.\\d{1,2}",  comparator: "REGEXP" //v1.2.3
                        }
                    }
                }
                steps {
                    script {
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.k8sdeploy("${env.K8S_PRD_FILE}", docker_image, "${env.PROD_NAMESPACE}")
                        echo "Deployed to Prod Successfully"
                    }
                }
            }
        }
    }
}


// Method for Docker build and Push
def dockerBuildAndPush(){
    return {
        echo "************************* Building Docker image*************************"
        sh "ls -la"
        sh "cp -r ${WORKSPACE}/* ./.cicd"
        sh "ls -la ./.cicd"
        sh "docker build --no-cache -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd"
        echo "************************ Login to Docker Registry ************************"
        sh "docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW}"
        sh "docker push ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
    }
}

def imageValidation() {
    return {
        println("Attemting to Pull the Docker Image")
        try {
            sh "docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            println("Image is Pulled Succesfully!!!!")
        }
        catch(Exception e) {
            println("OOPS!, the docker image with this tag is not available,So Creating the Image")
            buildApp().call()
            dockerBuildAndPush().call()
        }
    }
}




















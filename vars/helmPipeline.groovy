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
            choice(name: 'scanOnly',
                choices: 'no\nyes',
                description: 'This will scan your application'
            )
            choice(name: 'buildOnly',
                choices: 'no\nyes',
                description: 'This will Only Build your application'
            )
            choice(name: 'dockerPush',
                choices: 'no\nyes',
                description: 'This Will build dockerImage and Push'
            )
            choice(name: 'deployToDev',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Dev env'
            )
            choice(name: 'deployToTest',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Test env'
            )
            choice(name: 'deployToStage',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Stage env'
            )
            choice(name: 'deployToProd',
                choices: 'no\nyes',
                description: 'This will Deploy the app to Prod env'
            )
        }
        tools {
            maven 'Maven-3.8.8'
            jdk 'JDK-17'
        }
        environment {
            APPLICATION_NAME = "${pipelineParams.appName}"
            // DEV_HOST_PORT = "${pipelineParams.devHostPort}"
            // TST_HOST_PORT = "${pipelineParams.tstHostPort}"
            //STG_HOST_PORT = "${pipelineParams.stgHostPort}"
            //PRD_HOST_PORT = "${pipelineParams.prdHostPort}"
            HOST_PORT = "${pipelineParams.hostPort}"
            CONT_PORT = "${pipelineParams.contPort}"
            SONAR_TOKEN = credentials('sonar_creds')
            SONAR_URL = "http://34.55.191.104:9000"
            // https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#readmavenpom-read-a-maven-project-file
            // If any errors with readMavenPom, make sure pipeline-utility-steps plugin is installed in your jenkins, if not do install it
            // http://34.139.130.208:8080/scriptApproval/
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
           // DOCKER_HUB = "docker.io/i27devopsb4"
            DOCKER_CREDS = credentials('dockerhub_creds') //username and password
            K8S_DEV_FILE = "k8s_dev.yaml"
            K8S_TST_FILE = "k8s_tst.yaml"
            K8S_STG_FILE = "k8s_stg.yaml"
            K8S_PRD_FILE = "k8s_prd.yaml"
            DEV_NAMESPACE = "cart-dev-ns"
            TST_NAMESPACE = "cart-tst-ns"
            STG_NAMESPACE = "cart-stg-ns"
            PROD_NAMESPACE = "cart-prod-ns"
            JFROG_DOCKER_REGISTRY = "i27devopsb4.jfrog.io"
            JFROG_DOCKER_REPO_NAME = "cont-images-docker-docker"
            JFROG_CREDS = credentials('JFROG_CREDS')
            HELM_PATH = "${workspace}/i27-shared-lib/chart"
            DEV_ENV = "dev"
            TST_ENV = "tst"
            STAGE_ENV = "stage"
            PROD_ENV = "prd"

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
            stage ('Checkout Shared Lib'){
                steps {
                    script {
                        k8s.gitClone()
                    }
                }
            }
            stage ('Build') {
                when {
                    anyOf {
                        expression {
                            params.dockerPush == 'yes'
                            params.buildOnly == 'yes'
                        }
                    }
                }
                steps {
                    script {
                        docker.buildApp("${env.APPLICATION_NAME}") //appName
                    }
                }
            }
            stage ('Sonar') {
                when {
                    expression {
                        params.scanOnly == 'yes'
                    }
                    // anyOf {
                    //     expression {
                    //         params.scanOnly == 'yes'
                    //         params.buildOnly == 'yes'
                    //         params.dockerPush == 'yes'
                    //     }
                    // }
                }
                steps {
                    echo "Starting Sonar Scans"
                    withSonarQubeEnv('SonarQube'){ // The name u saved in system under manage jenkins
                        sh """
                        mvn  sonar:sonar \
                            -Dsonar.projectKey=i27-eureka \
                            -Dsonar.host.url=${env.SONAR_URL} \
                            -Dsonar.login=${SONAR_TOKEN}
                        """
                    }
                    timeout (time: 2, unit: 'MINUTES'){
                        waitForQualityGate abortPipeline: true
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
                        def docker_image = "${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        //envDeploy, hostPort, contPort)
                        imageValidation().call()
                        //dockerDeploy('dev', "${env.HOST_PORT}", "${env.CONT_PORT}").call()
                        k8s.k8sHelmChartDeploy("${env.APPLICATION_NAME}", "${env.DEV_ENV}", "${env.HELM_PATH}", "${GIT_COMMIT}")
                        //k8s.k8sdeploy("${env.K8S_DEV_FILE}", docker_image, "${env.DEV_NAMESPACE}")
                        echo "Deployed to Dev Successfully"
                    }
                }
            }
            stage ('Deploy to Test') {
                when {
                    expression {
                        params.deployToTest == 'yes'
                    }
                }
                steps {
                    script {
                        //envDeploy, hostPort, contPort)
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
                        //envDeploy, hostPort, contPort)
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        imageValidation().call()
                        k8s.k8sdeploy("${env.K8S_STG_FILE}", docker_image, "${env.STG_NAMESPACE}")
                        echo "Deployed to Stage Successfully"
                    }

                }
            }
            stage ('Deploy to Prod') {
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
                    timeout(time: 300, unit: 'SECONDS' ) { // SECONDS, MINUTES,HOURS{
                        input message: "Deploying to ${env.APPLICATION_NAME} to production ??", ok: 'yes', submitter: 'hemasre'
                    }
                    script {
                        //envDeploy, hostPort, contPort)
                        def docker_image = "${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                        k8s.k8sdeploy("${env.K8S_PRD_FILE}", docker_image, "${env.PROD_NAMESPACE}")
                        echo "Deployed to Prod Successfully"
                    }
                }
            }
            stage ('Clean') {
                steps {
                    echo "Cleaning the workspace"
                    cleanWs()
                }
            }
        }
    }


}

// Method for Maven Build
def buildApp() {
    return {
        echo "Building the ${env.APPLICATION_NAME} Application"
        sh 'mvn clean package -DskipTests=true'
    }
}

// Method for Docker build and Push
def dockerBuildAndPush(){
    return {
        echo "************************* Building Docker image*************************"
        sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd"
        echo "************************ Login to Docker Registry ************************"
        sh "docker login -u ${JFROG_CREDS_USR} -p ${JFROG_CREDS_PSW} i27devopsb4.jfrog.io"
        sh "docker push ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
    }
}

def imageValidation() {
    return {
        println("Attemting to Pull the Docker Image")
        try {
            sh "docker pull ${env.JFROG_DOCKER_REGISTRY}/${env.JFROG_DOCKER_REPO_NAME}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
            println("Image is Pulled Succesfully!!!!")
        }
        catch(Exception e) {
            println("OOPS!, the docker image with this tag is not available,So Creating the Image")
            buildApp().call()
            dockerBuildAndPush().call()
        }
    }
}










  
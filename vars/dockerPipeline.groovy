import com.i27academy.builds.Calculator
import com.i27academy.builds.Docker

def call(Map pipelineparams){
    // An instance of the class called calculator is created
    Calculator calculator = new Calculator(this)

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
            APPLICATION_NAME = ${pipelineParams.appName}
            SONAR_TOKEN = credentials('sonar_creds')
            SONAR_URL = "http://34.55.191.104:9000"
            // https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#readmavenpom-read-a-maven-project-file
            // If any errors with readMavenPom, make sure pipeline-utility-steps plugin is installed in your jenkins, if not do install it
            // http://34.139.130.208:8080/scriptApproval/
            POM_VERSION = readMavenPom().getVersion()
            POM_PACKAGING = readMavenPom().getPackaging()
            DOCKER_HUB = "docker.io/i27devopsb4"
            DOCKER_CREDS = credentials('dockerhub_creds') //username and password
        }
        stages {
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
                        buildApp().call()
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
                        //envDeploy, hostPort, contPort)
                        imageValidation().call()
                        dockerDeploy('dev', '5761', '8761').call()
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
                        imageValidation().call()
                        dockerDeploy('tst', '6761', '8761').call()
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
                        imageValidation().call()
                        dockerDeploy('stg', '7761', '8761').call()
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
                        dockerDeploy('prd', '8761', '8761').call()
                    }
                }
            }
        }
    }
}

// Method for Maven Build
// def buildApp() {
//     return {
//         echo "Building the ${env.APPLICATION_NAME} Application"
//         sh 'mvn clean package -DskipTests=true'
//     }
// }

// Method for Docker build and Push
def dockerBuildAndPush(){
    return {
        echo "************************* Building Docker image*************************"
        sh "cp ${WORKSPACE}/target/i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} ./.cicd"
        sh "docker build --no-cache --build-arg JAR_SOURCE=i27-${env.APPLICATION_NAME}-${env.POM_VERSION}.${env.POM_PACKAGING} -t ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT} ./.cicd"
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


// Method for deploying containers in diff env
def dockerDeploy(envDeploy, hostPort, contPort){
    return {
        echo "Deploying to $envDeploy Environmnet"
            withCredentials([usernamePassword(credentialsId: 'maha_ssh_docker_server_creds', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
                script {
                    sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip \"docker pull ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}\""
                    try {
                        // Stop the Container
                        sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker stop ${env.APPLICATION_NAME}-$envDeploy"
                        // Remove the Container
                        sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker rm ${env.APPLICATION_NAME}-$envDeploy"
                    }
                    catch(err) {
                        echo "Error Caught: $err"
                    }

                    // Create the container
                    sh "sshpass -p '$PASSWORD' -v ssh -o StrictHostKeyChecking=no $USERNAME@$dev_ip docker run -dit --name ${env.APPLICATION_NAME}-$envDeploy -p $hostPort:$contPort ${env.DOCKER_HUB}/${env.APPLICATION_NAME}:${GIT_COMMIT}"
                }   
            }
    }
}



// Eureka 
// continer port" 8761

// dev hp: 5761
// tst hp: 6761
// stg hp: 7761
// prod hp: 8761
















//withCredentials([usernameColonPassword(credentialsId: 'mylogin', variable: 'USERPASS')])

// https://docs.sonarsource.com/sonarqube/9.9/analyzing-source-code/scanners/jenkins-extension-sonarqube/#jenkins-pipeline

// sshpass -p password ssh -o StrictHostKeyChecking=no username@dockerserverip
 

 //usernameVariable : String
// Name of an environment variable to be set to the username during the build.
// passwordVariable : String
// Name of an environment variable to be set to the password during the build.
// credentialsId : String
// Credentials of an appropriate type to be set to the variable.

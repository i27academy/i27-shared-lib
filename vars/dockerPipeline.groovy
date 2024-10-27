import com.i27academy.builds.Calculator

def call(Map pipelineparams){
    // An instance of the class called calculator is created
    Calculator calculator = new Calculator(this)

    pipeline {
        agent any 
        environment {
            APP_NAME = "${pipelineparams.appName}" // this value should be coming from microservices 
        }
        stages {
            stage('AdditionStage') {
                steps {
                    script {
                    echo "Printing Sum of 2 numbers"
                    println calculator.add(3,4) // add expects 2 parameters
                    echo "****** Microservice Name is: ${APP_NAME} ********"
                    }
                }
            }
            stage ('SecondStage') {
                steps {
                    echo "Printing second stage"
                }
            }
        }
    }
}
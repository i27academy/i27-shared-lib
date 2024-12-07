package com.i27academy.k8s

// all the methods 
class K8s {
    def jenkins
    K8s(jenkins) {
        this.jenkins = jenkins
    }
    
    // Method to authenticate to kubernetes cluster
    def auth_login(){
        jenkins.sh """
        echo "********************* Entering into Kubernetes Authentication/Login Method *********************"
        gcloud compute instances list
        echo "********************* Get the K8S Node *********************"
        gcloud container clusters get-credentials i27-cluster --zone us-central1-c --project future-depth-439107-m2
        kubectl get nodes
        """
    }

    // Method to deploy the application
    def k8sdeploy(fileName, docker_image, namespace) {
        jenkins.sh """
        echo "********************* Entering into Kubernetes Deployment Method *********************"
        echo "Listing the files in the workspace"
        sed -i "s|DIT|${docker_image}|g" ./.cicd/${fileName}
        kubectl apply -f ./.cicd/${fileName} -n ${namespace}
        """
    }

   // Helm Deployments 
    def k8sHelmChartDeploy(appName, env, helmChartPath, imageTag, namespace) {
        jenkins.sh """
        echo "********************* Entering into Helm Deployment Method *********************"
        helm version
        # lets verify if chart exists
        echo "Verifying if the helm chart exists"
        if helm list -n ${namespace} | grep -q "eureka-dev-chart"; then
            echo "This chart exists"
            echo "Upgrading the chart"
            helm upgrade ${appName}-${env}-chart -f ./.cicd/helm_values/values_${env}.yaml --set image.tag=${imageTag} ${helmChartPath} -n ${namespace}
        else 
            echo "Chart doesnot exists"
            echo "Instlling the chart"
            helm install ${appName}-${env}-chart -f ./.cicd/helm_values/values_${env}.yaml --set image.tag=${imageTag} ${helmChartPath} -n ${namespace}
        fi
        """
    }

    // git clone 
    def gitClone() {
        jenkins.sh """
        echo "********************* Entering into Git Clone Method *********************"
        git clone -b master https://github.com/i27academy/i27-shared-lib.git
        echo "********************* Listing the files in the workspace *********************"
        ls -la
        """
    }

    //  Namespace Creation
    def namespace_creation(namespace_name){
        jenkins.sh """#!/bin/bash
        # Script to create namespace, if doesnot exists
        #!/bin/bash
        #namespace_name="boutique"
        echo "Namespace Provided is ${namespace_name}"
        # Validate if the namespace exists
        if kubectl get ns "${namespace_name}" &> /dev/null ; then 
        echo "Your Namespace '${namespace_name}' exists!!!!!!"
        exit 0
        else
        echo "Your namespace '${namespace_name}' doesnot exists, so creating it!!!!!!"
        if kubectl create ns '${namespace_name}' &> /dev/null; then
          echo "Your namespace '${namespace_name}' has created succesfully"
          exit 0
        else 
          echo "Some error , failed to create '${namespace_name}'"
          exit 1
        fi
        fi
        """
    }

}



// sudo apt-get update

// sudo apt-get install apt-transport-https ca-certificates gnupg curl -y

// curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg

// echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] https://packages.cloud.google.com/apt cloud-sdk main" | sudo tee -a /etc/apt/sources.list.d/google-cloud-sdk.list

// sudo apt-get update && sudo apt-get install google-cloud-cli -y

// gcloud 
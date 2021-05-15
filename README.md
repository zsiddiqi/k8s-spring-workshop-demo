# Spring on Kubernetes

## 1: Workshop Overview

During this workshop you will learn the finer details of how to create, build, run, and debug a basic Spring Boot app on Kubernetes by doing the following:

- Create a basic Spring Boot app
- Build a Docker image for the app
- Push the image to a Docker registry
- Deploy and run the app on Kubernetes
- Test the app using port-forwarding and ingress
- Use skaffold to iterate easily as you work on your app
- Use kustomize to manage configurations across environments
- Externalize application configuration using ConfigMaps
- Use service discovery for app-to-app communication
- Deploy the Spring PetClinic App with MySQL

## 2: Setup Environment

### Workshop Command Execution

This workshop uses action blocks for various purposes, anytime you see such a block with an icon on the right hand side, you can click on it and it will perform the listed action for you.

> This is a real environment where action blocks can create real apps and Kubernetes clusters. Please, wait for an action to fully execute before proceeding to the next so the workshop behaves as expected.

### Workshop Terminals

Two terminals are included in this workshop, you will mainly use terminal 1, but if it's busy you can use terminal 2 .

Try the action block's bellow

`echo "Hi I'm terminal 1"`

`echo "Hi I'm terminal 2"`

### Workshop Code Editor

The workshop features a built in code editor you can use by pressing the **Editor** tab button. Pressing the refresh button in the workshop's UI can help the editor load when switching tabs. The files in this editor automatically save.

The editor takes a few moments to load, please select the Editor tab now to display it, or click on the action block below.

### Workshop Console (Octant)

You will have the ability to inspect your Kubernetes cluster with Octant, an Open Source developer-centric web interface for Kubernetes that lets you inspect a Kubernetes cluster and its applications.

You haven't deployed anything to Kubernetes so there isn't much to display at the moment. When you get to the section **7: Deploying to Kubernetes**, you will have a Kubernetes cluster and a Spring Boot app to inspect with Octant.

## 3: Create a Spring Boot app

### Getting Started

First we will create a directory for our app and use start.spring.io to create a basic Spring Boot application.

`mkdir demo && cd demo`

Download and extract the project from the Spring Initializr

```
curl https://start.spring.io/starter.tgz -d artifactId=k8s-demo-app -d name=k8s-demo-app -d packageName=com.example.demo -d dependencies=web,actuator -d javaVersion=11 | tar -xzf -
```

### Modify K8sDemoApplication.java and create your Rest controller

First, add the annotations and @RestController

> demo/src/main/java/com/example/demo/K8sDemoAppApplication.java

```
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
```

Now, add your 'Hello World' rest controller

```
@GetMapping("/")
public String hello() {
    return "Hello World\n";
}
```

Your file should look like the following:

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class K8sDemoAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(K8sDemoAppApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        return "Hello World\n";
    }
}
```

## 4: Run the App

In a terminal window run the following command. The application will start on port 8080.

`./mvnw spring-boot:run`

### Test The App

Make an HTTP request to _http://localhost:8080_ in another terminal

`curl http://localhost:8080`

### Test Spring Boot Actuator

[Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html) adds several other endpoints to our app. By default Spring Boot will expose a **health** and **info** endpoint.

`curl localhost:8080/actuator | jq .`

Your output will be similar to this.

```json
{
  "_links": {
    "self": {
      "href": "http://localhost:8080/actuator",
      "templated": false
    },
    "health": {
      "href": "http://localhost:8080/actuator/health",
      "templated": false
    },
    "info": {
      "href": "http://localhost:8080/actuator/info",
      "templated": false
    }
}
```

Be sure to stop the Java process before continuing on or else you might get port binding issues since Java is using port **8080**

`<ctrl+c>`

## 5: Containerize the App

> alt_text

### Building A Container

- Spring Boot 2.3.x and newer can build a container for you without the need for any additional plugins or files
- To do this use the Spring Boot Build plugin goal **build-image**

`./mvnw spring-boot:build-image`

- Running **docker images** will allow you to see the built container

`docker images`

### Run The Container

`docker run --name k8s-demo-app -p 8080:8080 k8s-demo-app:0.0.1-SNAPSHOT`

### Test The App Responds

`curl http://localhost:8080`

Be sure to stop the Docker container before continuing.

`docker rm -f k8s-demo-app`

## 6: Putting The Container In A Registry

- Up until this point the container only lives on your machine
- It is useful to instead place the container in a registry
  - In a real life scenario this allows others to use the container
- **Docker Hub** is a popular public registry
- Private registries exist as well. In this lab you will be using a private registry on localhost.

### Run The Build And Deploy The Container

- For convenience, the address of the local private docker registry for this lab is saved in an environment variable. You can see it by running the following command.

`echo $REGISTRY_HOST`

Run the following Maven command to re-build the image, this time including the registry address in the image name by setting the property **spring-boot.build-image.imageName**.

`./mvnw spring-boot:build-image -Dspring-boot.build-image.imageName=$REGISTRY_HOST/apps/demo`

Now we can push the container to the local container registry

`docker push $REGISTRY_HOST/apps/demo`

If you query the registry you should now see the image

`skopeo list-tags docker://$REGISTRY_HOST/apps/demo`

You should see a print out like the this.

```json
{
  "Repository": "lab-workshop-1-w01-s001-registry.192.168.64.2.nip.io/apps/demo",
  "Tags": ["latest"]
}
```

## 7: Deploying to Kubernetes

- With our container built and deployed to a registry you can now run this container on Kubernetes

### Deployment Descriptor

- Kubernetes uses YAML files to provide a way of describing how the app will be deployed to the platform
- You can write these by hand using the Kubernetes documentation as a reference
- Or you can have Kubernetes generate them for you using kubectl
- The `--dry-run` flag allows us to generate the YAML without actually deploying anything to Kubernetes

Lets create a directory to put the files we will need to deploy our application to Kubernetes

`mkdir k8s`

The first file we need is a deployment descriptor. Execute the following command to create the file

```
kubectl create deployment k8s-demo-app --image eduk8s-labs-w05-s032-registry.reg-prod-e7decde.tanzu-labs.esp.vmware.com/apps/demo -o yaml --dry-run=client > ~/demo/k8s/deployment.yaml
```

- The resulting **k8s/deployment.yaml** should look like this. You can switch to the Editor tab to compare with the file you just created.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: k8s-demo-app
  name: k8s-demo-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: k8s-demo-app
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: k8s-demo-app
    spec:
      containers:
        - image: eduk8s-labs-w05-s032-registry.reg-prod-e7decde.tanzu-labs.esp.vmware.com/apps/demo
          name: k8s-demo-app
          resources: {}
status: {}
```

### Service Descriptor

- A service acts as a load balancer for the pod created by the deployment descriptor
- If we want to be able to scale pods than we want to create a service for those pods

`kubectl create service clusterip k8s-demo-app --tcp 80:8080 -o yaml --dry-run=client > ~/demo/k8s/service.yaml`

The resulting **service.yaml** should look similar to this. Again, you can switch to the Editor tab to compare with the file you just created.

```yaml
apiVersion: v1
kind: Service
metadata:
  creationTimestamp: null
  labels:
    app: k8s-demo-app
  name: k8s-demo-app
spec:
  ports:
    - name: 80-8080
      port: 80
      protocol: TCP
      targetPort: 8080
  selector:
    app: k8s-demo-app
  type: ClusterIP
status:
  loadBalancer: {}
```

### Apply The Deployment and Service YAML

- The deployment and service descriptors have been created in the /k8s directory
- Since we used the `--dry-run` flag to generate them, they have not yet been applied to Kubernetes
- Apply these now to get everything running
- You can watch as the pods and services get created
- To apply your manifest files and get the app running, run the following command

`kubectl apply -f ~/demo/k8s`

Execute the following **watch** command to see the deployment progress in Kubernetes

`watch -n 1 kubectl get all`

You should see something like the following:

```
NAME                               READY   STATUS    RESTARTS   AGE
pod/k8s-demo-app-d6dd4c4d4-7t8q5   1/1     Running   0          68m

NAME                   TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)   AGE
service/k8s-demo-app   ClusterIP   10.100.200.243   <none>        80/TCP    68m

NAME                           READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/k8s-demo-app   1/1     1            1           68m

NAME                                     DESIRED   CURRENT   READY   AGE
replicaset.apps/k8s-demo-app-d6dd4c4d4   1         1         1       68m
```

> `watch` is a useful command line tool that you can install on Linux and OSX. All it does is continuously execute the command you pass it. You can just run the `kubectl` command specified after the `watch` command but the output will be static as opposed to updating constantly.

Terminate the watch process before to continuing.

`<ctrl+c>`

## 8: Testing the App

- The service is assigned a cluster IP, which is only accessible from inside the cluster.

To see this run the next command.

`kubectl get service/k8s-demo-app`

You should have gotten output like the following.

```
NAME                   TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)   AGE
service/k8s-demo-app   ClusterIP   10.100.200.243   <none>        80/TCP    68m
```

- To access the app we can use **kubectl port-forward** to forward requests on port **8080** locally to port **80** on the service running in Kubernetes

`kubectl port-forward service/k8s-demo-app 8080:80`

Now we can `curl` localhost:8080 and it will be forwarded to the service in the cluster

`curl http://localhost:8080`

Congrats you have deployed your first app to Kubernetes 🎉

Be sure to stop the **kubectl port-forward** process before moving on

`<ctrl+c>`

## 9: Exposing the Service

- If we want to expose the service outside of the cluster we can use an Ingress.
  An Ingress is an API object that defines rules which allow external access to services in a cluster. An Ingress controller fulfills the rules set in the Ingress.

Next, you will go create **ingress.yaml** so you can access your app outside of the cluster.

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: k8s-demo-app
  labels:
    app: k8s-demo-app
spec:
  rules:
    - host: k8s-demo-app-eduk8s-labs-w05-s032.reg-prod-e7decde.tanzu-labs.esp.vmware.com
      http:
        paths:
          - path: "/"
            pathType: Prefix
            backend:
              service:
                name: k8s-demo-app
                port:
                  number: 80
```

Now, apply the **ingress.yaml**.

`kubectl apply -f ~/demo/k8s`

## 10: Testing the Public Ingress

Use the following command to view the host and IP address.

> The `-w` option of **kubectl** lets you watch a single Kubernetes resource.

`kubectl get ingress k8s-demo-app -w`

You should see output like the following. Depending on the ingress being used in the Kubernetes cluster you might need to wait for an IP address to be assigned to the ingress. If you are running this workshop in a Cloud environment (Google, Amazon, Azure etc.), or locally in a cluster like Minikube, you may need to wait for an IP address to be assigned. In the case of a cloud environment, Kubernetes will assign the service an external IP. In this hosted lab environment, only the host name will be provided, so you will not see an IP address assigned.

```
NAME           CLASS    HOSTS                                                                          ADDRESS                                                                   PORTS   AGE
k8s-demo-app   <none>   k8s-demo-app-eduk8s-labs-w01-s010.s1t-prod-31a4032.tanzu-labs.esp.vmware.com   a42e0185b95f34449943ea4c560be1d3-1763013971.us-east-2.elb.amazonaws.com   80      90s
```

Exit from the watch command

`<ctrl+c>`

Test the ingress configuration execute the following command.

`curl k8s-demo-app-eduk8s-labs-w05-s032.reg-prod-e7decde.tanzu-labs.esp.vmware.com`

## 11: Best Practices

- Kubernetes uses two probes to determine if the app is ready to accept traffic and whether the app is alive
- If the liveness probe does not return a 200 Kubernetes will restart the Pod
- If the readiness probe does not return a 200 no traffic will be routed to it
- Spring Boot has a built in set of endpoints from the Actuator module that fit nicely into these use cases
  - The /health/readiness endpoint can be used for the readiness probe
  - The /health/liveness endpoint can be used for the liveness probe

> The /health/readiness and /health/liveness endpoints are only available in Spring Boot 2.3.x. The/health and /info endpoints are reasonable starting points in earlier versions.

## 12: Add Readiness and Liveness Probes

Add The Readiness Probe

```yaml
readinessProbe:
  httpGet:
    port: 8080
    path: /actuator/health/readiness
```

Add The Liveness Probe

```
livenessProbe:
  httpGet:
    port: 8080
    path: /actuator/health/liveness
```

## 13: Graceful Shutdown

- Due to the asynchronous way Kubnernetes shuts down applications there is a period of time when requests can be sent to the application while an application is being terminated.
- To deal with this we can configure a pre-stop sleep to allow enough time for requests to stop being routed to the application before it is terminated.
  Add a **preStop** command to the **podspec** of your **deployment.yaml**

```yaml
lifecycle:
  preStop:
    exec:
      command:
        - sh
        - "-c"
        - sleep 10
```

## 14: Handling In Flight Requests

- Our application could also be handling requests when it receives the notification that it needs to shut down.
- In order for us to finish processing those requests before the application shuts down we can configure a “grace period” in our Spring Boot applicaiton.
- Open `application.properties` in `/src/main/resources` and add

`server.shutdown=graceful`

There is also a `spring.lifecycle.timeout-per-shutdown-phase` (default 30s).

> `server.shutdown` is only available begining in Spring Boot 2.3.x

## 15: Update The Container & Apply The Updated Deployment YAML

Let’s update the pom.xml to configure the image name explicitly:

```xml
<spring-boot.build-image.imageName>eduk8s-labs-w05-s032-registry.reg-prod-e7decde.tanzu-labs.esp.vmware.com/apps/demo</spring-boot.build-image.imageName>
```

Then we can build and push a new image to our repository:

`./mvnw clean spring-boot:build-image`

`docker push eduk8s-labs-w05-s032-registry.reg-prod-e7decde.tanzu-labs.esp.vmware.com/apps/demo`

When we apply our resources again the old container will be terminated and a new one will be deployed with the new changes.

`kubectl apply -f ~/demo/k8s`

You can run the following command to watch Kubernetes terminate the old container and redeploy a new one in real time. Notice the status of the older pod changing from **Running** to **Terminating** before the old pod disappears.

`watch -n 1 kubectl get all`

You can still test with curl.

`curl k8s-demo-app-eduk8s-labs-w05-s032.reg-prod-e7decde.tanzu-labs.esp.vmware.com`

To exit running process in terminal one.

`<ctrl+c>`

## 16: Cleaning-up

- Before we move on to the next section lets clean up everything we deployed

`kubectl delete -f ~/demo/k8s`

17: Skaffold

Thus far, to deploy any changes to the app, we have needed to re-build the image, push it to the docker registry, recreate the pod in Kubernetes, and potentially port-forward for testing. We can automate these steps (and more) using a command line tool called [Skaffold](https://github.com/GoogleContainerTools/skaffold).

- Skaffold facilitates continuous development for Kubernetes applications by handling the workflow for building, pushing and deploying your application.
- It simplifies the development process by combining multiple steps into one easy command
- It provides the building blocks for a CI/CD process

Confirm Skaffold is installed by running the following command

`skaffold version`

### Adding Skaffold YAML

- Skaffold is configured using…you guessed it…another YAML file
- Create a YAML file skaffold.yaml in the root of the project

```yaml
apiVersion: skaffold/v2beta12
kind: Config
metadata:
  name: k-s-demo-app--
build:
  artifacts:
    - image: eduk8s-labs-w05-s032-registry.reg-prod-e7decde.tanzu-labs.esp.vmware.com/apps/demo
      buildpacks:
        builder: docker.io/paketobuildpacks/builder:base
        dependencies:
          paths:
            - src
            - pom.xml
deploy:
  kubectl:
    manifests:
      - k8s/deployment.yaml
      - k8s/service.yaml
      - k8s/ingress.yaml
portForward:
  - resourceType: service
    resourceName: k8s-demo-app
    port: 80
    localPort: 4503
```

The **builder** is the same one used by Spring Boot when it builds a container from the build plugins (you would see it logged on the console when you build the image).

An alternative syntax for the **build** section would be to use **custom** instead of **buildpacks** configuration. For example:

```yaml
custom:
  buildCommand: ./mvnw spring-boot:build-image -D spring-boot.build-image.imageName=$IMAGE && docker push $IMAGE
```

## 18: Development with Skaffold

Skaffold makes some enhancements to our development workflow when using Kubernetes

- Build the app and create the container (buildpacks)
- Push the container to the registry (Docker)
- Apply the deployment and service YAMLs
- Stream the logs from the Pod to your terminal
- Automatically set up port forwarding

Run the following command to have Skaffold build and deploy our application to Kubernetes.

`skaffold dev --port-forward`

### Testing Everything Out

If you use `watch` to view your Kubernetes resources you will see the same resources created as before

`watch -n 1 kubectl get all`

After the resources are created you can stop the `watch` command.

`<ctrl+c>`

When running `skaffold dev --port-forward` you will see a line in your console that looks like this

```
Port forwarding service/k8s-demo-app in namespace eduk8s-labs-w01-s010, remote port 80 -> address 127.0.0.1 port 4503
```

In this case port 4503 will be forwarded to port 80 of the service

> NOTE: Your port may be different from **4503** please verify with the output from `skaffold dev --port-forward` and substitute your correct port if needed.

`curl localhost:4503`

## 19: Make Changes to the Controller

Skaffold will watch the project files for changes. Open `K8sDemoApplication.java` and change the **hello** method to return **Hola World**.

`return`

Once you save the file, return to the Terminal and you will notice Skaffold rebuild and redeploy everything with the new change

When you see the port forwarding message again, execute the following command and notice it now returns **Hola World**.

curl localhost:4503

## 20: Cleaning Everything Up

Once we are done, if we kill the **skaffold** process, Skaffold will remove all the resources. Just hit **CTRL-C** in the terminal where **skaffold** is running…

To exit Skaffold in terminal one.

`<ctrl+c>`

You should see the following output.

```
...
WARN[2086] exit status 1
Cleaning up...
 - deployment.apps "k8s-demo-app" deleted
 - service "k8s-demo-app" deleted
 - ingress.networking.k8s.io "k8s-demo-app" deleted
```

## 21: Debugging with Skaffold

Skaffold also makes it easy to attach a debugger to the container running in Kubernetes

`skaffold debug --port-forward`

```
...
Port forwarding service/k8s-demo-app in namespace rbaxter, remote port 80 -> address 127.0.0.1 port 4503
Watching for changes...
Port forwarding pod/k8s-demo-app-75d4f4b664-2jqvx in namespace rbaxter, remote port 5005 -> address 127.0.0.1 port 5005
...
```

The **debug** command results in two ports being forwarded

- The http port, **4503** in the above example
- The remote debug port **5005** in the above example

Set a breakpoint where we return **Hola World** from the **hello** method in **K8sDemoApplication.java**

`return`

Example: To add a breakpoint, click to the left of the line number in the Editor. Notice the dot on the return statement (Click image to enlarge).

> alt_text

## 22: Create a Launcher

To debug our application running in Kubernetes we need to create a remote debug configuration in our IDE. This will work for any remote process (doesn’t have to be running in Kubernetes).

On the left hand side of the IDE tab, click the Run/Debug icon to open the Run/Debug panel, then click on the **create a launch.json** file link.

> alt_text

The IDE will create a default launch configuration for the current file and for **K8sDemoAppApplication**. We need to add another configuration for remote debugging.

Copy and paste the following JSON snippet into your **launch.json** ABOVE the first json entry (inside the **configurations** block).

```json
{
    "type": "java",
    "name": "Debug (Attach)",
    "request": "attach",
    "hostName": "localhost",
    "port": 5005
},
```

Now select the **Debug (Attached)** option from the drop down and click the Run button

This should attach the debugger to the remote port (5005) established by Skaffold

> alt_text

Now you can execute the following command to make a request to the application. You will not see a response immediately because the debugger in the IDE will break at our breakpoint. Return to the Editor and notice the debug toolbar in the top center of the screen, and the debug info frames on the left and bottom of the screen. You can step through the code, view variable values, etc.

`curl localhost:4503`

Stop the **Skaffold** process by executing the following command

`<ctrl+c>`

## 23: Kustomize

So far, we have deployed our application to a single Kubernetes environment. Most likely, we would want to deploy it to several environments (e.g. dev, test, prod). We could create separate Kubernetes deployment manifests for each environment, or we could look to tools to help de-duplicate and manage environment-specific differences in our configuration.

In this section we will look at one such tool: [Kustomize](https://kustomize.io/) allows us to customize deployments to different environments.

We can start with a base set of resources and then apply customizations on top of those.

Features

- Allows easier deployments to different environments/providers
- Allows you to keep all the common properties in one place
- Generate configuration for specific environments
- No templates, no placeholder spaghetti, no environment variable overload

## 24: Getting Started with Kustomize

Create a new directory in the root of our project called **kustomize/base**

`mkdir -p kustomize/base`

Move the `deployment.yaml` and `service.yaml` from the `k8s` directory into `kustomize/base`

`mv k8s/* kustomize/base`

Delete the k8s directory since we no longer need it

`rm -Rf k8s`

## 25: kustomization.yaml

- In `kustomize/base` create a new file called `kustomization.yaml` and add the following to it.

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - service.yaml
  - deployment.yaml
  - ingress.yaml
```

> NOTE: Optionally, you can now remove all the labels and annotations in the metadata of objects and specs inside objects. Kustomize adds default values that link objects to each other (e.g. to link a service to a deployment). If there is only one of each in your manifest then it will pick something sensible.

## 26: Kustomizing Our Deployment

Lets imagine we want to deploy our app to a QA environment, but in this environment we want to have two instances of our app running. To do this we can create a new Kustomization for this environment which specifies the number of instances we want deployed.

Create a new directory called `qa` under the `kustomize` directory

`mkdir -p kustomize/qa`

Create a new file in `kustomize/qa` called `update-replicas.yaml`, this is where we will specify that we want 2 replicas running

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: k8s-demo-app
spec:
  replicas: 2
```

Create a new file called `kustomization.yaml` in `kustomize/qa` and add the following to it

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../base

patchesStrategicMerge:
  - update-replicas.yaml
```

Here we tell Kustomize that we want to patch the resources from the **base** directory with the `update-replicas.yaml` file. Notice that in `update-replicas.yaml` we are just updating the properties we care about, in this case the **replicas**.

## 27: Running Kustomize

You can use `kustomize build` to build the customizations and produce all of the YAML to deploy our application. This command will return the generated YAML as output in the terminal.

To build the base profile for our application run the following command.

`kustomize build ./kustomize/base`

When we build the QA customization the replicas property is updated to **2**.

`kustomize build ./kustomize/qa`

```yaml
---
spec:
  replicas: 2
```

## 28: Piping from Kustomize

We can pipe the output from **kustomize** into **kubectl** in order to use the generated YAML to deploy our app to Kubernetes

`kustomize build kustomize/qa | kubectl apply -f -`

If you are watching the pods in your Kubernetes namespace you will see two pods created instead of one because we generated the YAML using the QA customization.

`watch -n 1 kubectl get all`

```bash
Every 1.0s: kubectl get all                                 eduk8s-labs-w01-s010-669db9789f-dkdd5: Mon Feb  3 12:00:04 2020

NAME                                READY   STATUS    RESTARTS   AGE
pod/k8s-demo-app-647b8d5b7b-r2999   1/1     Running   0          83s
pod/k8s-demo-app-647b8d5b7b-x4t54   1/1     Running   0          83s

NAME                   TYPE        CLUSTER-IP       EXTERNAL-IP   PORT(S)   AGE
service/k8s-demo-app   ClusterIP   10.100.200.200   <none>        80/TCP    84s

NAME                           READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/k8s-demo-app   2/2     2            2           84s

NAME                                      DESIRED   CURRENT   READY   AGE
replicaset.apps/k8s-demo-app-647b8d5b7b   2         2         2       84s
```

To exit the watch command

`<ctrl+c>`

## 29: Clean Up

- Before continuing clean up your Kubernetes environment

`kustomize build kustomize/qa | kubectl delete -f -`

## 30: Using Kustomize with Skaffold

Currently our Skaffold configuration uses **kubectl** to deploy our artifacts, but we can change that to use **kustomize**.

First, delete your previous **skaffold.yaml**

`rm skaffold.yaml`

Create a new **skaffold.yaml** using the following content.

```yaml
apiVersion: skaffold/v2beta5
kind: Config
metadata:
  name: k-s-demo-app
build:
  artifacts:
    - image: eduk8s-labs-w05-s032-registry.reg-prod-e7decde.tanzu-labs.esp.vmware.com/apps/demo
      buildpacks:
        builder: gcr.io/paketo-buildpacks/builder:base-platform-api-0.3
        dependencies:
          paths:
            - src
            - pom.xml
deploy:
  kustomize:
    paths: ["kustomize/base"]
profiles:
  - name: qa
    deploy:
      kustomize:
        paths: ["kustomize/qa"]
portForward:
  - resourceType: service
    resourceName: k8s-demo-app
    port: 80
    localPort: 4503
```

- Notice now the **deploy** property has been changed from **kubectl** to now use **kustomize**
- Also notice that we have a new profiles section allowing us to deploy our QA configuration using Skaffold

## 31: Testing Skaffold + Kustomize

If you run **skaffold** without specifying a **profile** parameter Skaffold will use the deployment configuration from **kustomize/base**.

`skaffold dev --port-forward`

Terminate the currently running Skaffold process.

`<ctrl+c>`

Run the following command specifying the **qa** profile to test out the QA deployment.

`skaffold dev -p qa --port-forward`

Test your deployment freely. You can validate that two pods are created when the qa profile is specified.

`kubectl get all`

You can also validate that the app is functional.

`curl localhost:4503`

Be sure to kill the **skaffold** process before continuing.

`<ctrl+c>`

## 32: Externalized Configuration

- One of the [12 factors for cloud native apps](https://12factor.net/config) is to externalize configuration
- Kubernetes provides support for externalizing configuration via ConfigMaps and Secrets

We can create a ConfigMap or secret easily using **kubectl**. For example

```
kubectl create configmap log-level --from-literal=LOGGING_LEVEL_ORG_SPRINGFRAMEWORK=DEBUG

kubectl get configmap log-level -o yaml
```

```yaml
apiVersion: v1
data:
  LOGGING_LEVEL_ORG_SPRINGFRAMEWORK: DEBUG
kind: ConfigMap
metadata:
  creationTimestamp: "2020-02-04T15:51:03Z"
  name: log-level
  namespace: eduk8s-labs-w05-s032
  resourceVersion: "2145271"
  selfLink: /api/v1/namespaces/default/configmaps/log-level
  uid: 742f3d2a-ccd6-4de1-b2ba-d1514b223868
```

## 33: Using ConfigMaps in Our Apps

- There are a number of ways to consume the data from ConfigMaps in our apps
- Perhaps the easiest is to use the data as environment variables
- To do this we need to change our **deployment.yaml** in **kustomize/base**

Add the **envFrom** properties from the previous module which reference our ConfigMap **log-level**

```yaml
envFrom:
  - configMapRef:
      name: log-level
```

Update the deployment by running **skaffold dev** (so we can stream the logs)

`skaffold dev`

If everything worked correctly you should see much more verbose logging in your console

Be sure to kill the **skaffold** process before continuing

`<ctrl+c>`

## 34: Removing The Config Map and Reverting The Deployment

- Before continuing lets remove our config map and revert the changes we made to **deployment.yaml** To delete the ConfigMap run the following command

`kubectl delete configmap log-level`

In **kustomize/base/deployment.yaml** remove the **envFrom** properties we added

`sed -i '39,41d' ~/demo/kustomize/base/deployment.yaml`

- Next we will use Kustomize to make generating ConfigMaps easier

Your **deployment.yaml** should be like this:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: k8s-demo-app
  name: k8s-demo-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: k8s-demo-app
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: k8s-demo-app
    spec:
      containers:
        - image: eduk8s-labs-w05-s032-registry.reg-prod-e7decde.tanzu-labs.esp.vmware.com/apps/demo
          name: demo
          resources: {}
          readinessProbe:
            httpGet:
              port: 8080
              path: /actuator/health/readiness
          livenessProbe:
            httpGet:
              port: 8080
              path: /actuator/health/liveness
          lifecycle:
            preStop:
              exec:
                command:
                  - sh
                  - "-c"
                  - sleep 10
status: {}
```

## 35: Config Maps and Spring Boot Application Configuration

- In Spring Boot we usually place our configuration values in application properties or YAML files
- ConfigMaps in Kubernetes can be populated with values from files, like properties or YAML files
- We can do this via **kubectl**

`kubectl create configmap k8s-demo-app-config --from-file ./path/to/application.yaml`

No need to execute the above command, it is just an example, the following sections will show a better way.

- We can then mount this config map as a volume in our container at a directory Spring Boot knows about and Spring Boot will automatically recognize the file and use it

## 36: Creating A Config Map With Kustomize

Kustomize offers a way of generating ConfigMaps and Secrets as part of our customizations.

Create a file called **application.yaml** in **kustomize/base** and add the following content

```yaml
logging:
  level:
    org:
      springframework: INFO
```

- We can now tell Kustomize to generate a ConfigMap from this file, in **kustomize/base/kustomization.yaml** by adding the following snippet to the end of the file

```yaml
configMapGenerator:
  - name: k8s-demo-app-config
    files:
      - application.yaml
```

- If you now run `$ kustomize build` you will see a ConfigMap resource is produced

`kustomize build kustomize/base`

```yaml
apiVersion: v1
data:
  application.yaml: |-
    logging:
      level:
        org:
          springframework: INFO
kind: ConfigMap
metadata:
  name: k8s-demo-app-config-fcc4c2fmcd
```

By default **kustomize** generates a random name suffix for the ConfigMap. Kustomize will take care of reconciling this when the **ConfigMap** is referenced in other places (ie in volumes). It does this to force a change to the **Deployment** and in turn force the app to be restarted by Kubernetes. This isn’t always what you want, for instance if the **ConfigMap** and the **Deployment** are not in the same **Kustomization**. If you want to omit the random suffix, you can set `behavior=merge` (or `replace`) in the **configMapGenerator**.

Now edit **deployment.yaml** in **kustomize/base** to have Kubernetes create a volume for the ConfigMap and mount that volume in the container

Create the volume.

```yaml
volumes:
  - name: config-volume
    configMap:
      name: k8s-demo-app-config
```

Mount the volume.

```yaml
volumeMounts:
  - name: config-volume
    mountPath: /workspace/config
```

- In the above **deployment.yaml** we are creating a volume named **config-volume** from the ConfigMap named **k8s-demo-app-config**
- In the container we are mounting the volume named **config-volume** within the container at the path **/workspace/config**
- Spring Boot automatically looks in `./config` for application configuration and if present will use it (because the app is running in `/workspace`)

Your **deployment.yaml**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: k8s-demo-app
  name: k8s-demo-app
spec:
  replicas: 1
  selector:
    matchLabels:
      app: k8s-demo-app
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: k8s-demo-app
    spec:
      containers:
        - image: lab-workshop-1-w01-s001-registry.192.168.64.7.nip.io/apps/demo
          name: demo
          resources: {}
          readinessProbe:
            httpGet:
              port: 8080
              path: /actuator/health/readiness
          livenessProbe:
            httpGet:
              port: 8080
              path: /actuator/health/liveness
          lifecycle:
            preStop:
              exec:
                command:
                  - sh
                  - "-c"
                  - sleep 10
          volumeMounts:
            - name: config-volume
              mountPath: /workspace/config
      volumes:
        - name: config-volume
          configMap:
            name: k8s-demo-app-config
status: {}
```

Your **base/kustomization.yaml**

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - service.yaml
  - deployment.yaml
  - ingress.yaml
configMapGenerator:
  - name: k8s-demo-app-config
    files:
      - application.yaml
```

Your **base/application.yaml**

```yaml
logging:
  level:
    org:
      springframework: INFO
```

\*\* 37: Testing Our New Deployment

If you run `$ skaffold dev --port-forward` everything should deploy as normal

`skaffold dev --port-forward`

Check that the ConfigMap was generated

`kubectl get configmap`

You should see something similar to this

```
NAME                             DATA   AGE
k8s-demo-app-config-fcc4c2fmcd   1      18s
```

Skaffold is watching our files for changes, go ahead and change `logging.level.org.springframework` from _INFO_ to _DEBUG_ and Skaffold will automatically create a new ConfigMap and restart the pod

- This is the field that you will be changing

`springframework`

To change INFO to DEBUG

`sed -i s/INFO/DEBUG/g ~/demo/kustomize/base/application.yaml`

You should see a lot more logging in your terminal once the new pod starts

Be sure to kill the **skaffold** process before continuing

`<ctrl+c>`

Also, you will want to go back to **application.properties** in **kustomize/base** and change `logging.level.org.springframework` back to _INFO_.

sed -i s/DEBUG/INFO/g ~/demo/kustomize/base/application.yaml

To verify **springframework** is _INFO_ again:

`springframework`

## 38: Service Discovery

- Kubernetes makes it easy to make requests to other services
- Each service has a DNS entry in the container of the other services allowing you to make requests to that service using the service name
- For example, if there is a service called **k8s-workshop-name-service** deployed we could make a request from the k8s-demo-app just by making an HTTP request to **http://k8s-workshop-name-service**

## 39: Deploying Another App

- In order to save time in the following modules we will use an [existing app](https://github.com/ryanjbaxter/k8s-spring-workshop/tree/master/name-service) that returns a random name
- The image for this service resides in [Docker Hub](https://hub.docker.com/repository/docker/ryanjbaxter/k8s-workshop-name-service) (a public container registry)
- To make things easier we placed a [Kustomization file](https://github.com/ryanjbaxter/k8s-spring-workshop/blob/master/name-service/kustomize/base/kustomization.yaml) in the GitHub repo that we can reference from our own Kustomization file to deploy the app to our cluster

## 40: Modify `kustomization.yaml`

In your k8s-demo-app’s **kustomize/base/kustomization.yaml** add a new resource pointing to the new app’s **kustomize** directory

`- https://github.com/ryanjbaxter/k8s-spring-workshop/name-service/kustomize/base`

## 41: Making A Request To The Service

Delete previous implementation of the **hello** method in **K8sDemoAppApplication.java**

`sed -i '19d' ~/demo/src/main/java/com/example/demo/K8sDemoAppApplication.java`

Modify the _hello_ method of _K8sDemoApplication.java_ to make a request to the new service.

First add the appropriate imports for **RestTemplate** and **RestTemplateBuilder**.

```java
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
```

Instantiate a new **RestTemplate** instance.

```java
private RestTemplate rest = new RestTemplateBuilder().build();
```

Finally use the **RestTemplate** to make a **GET** request to the **k8s-workshop-name-service**.

```java
String name = rest.getForObject("http://k8s-workshop-name-service", String.class);
return "Hola " + name;
```

- Notice the hostname of the request we are making matches the service name in [our service.yaml](https://github.com/ryanjbaxter/k8s-spring-workshop/blob/master/name-service/kustomize/base/service.yaml#L7) file

To verify your work, this is what your **K8sDemoAppApplication.java** file should look like.

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

@RestController
@SpringBootApplication
public class K8sDemoAppApplication {
private RestTemplate rest = new RestTemplateBuilder().build();

    public static void main(String[] args) {
        SpringApplication.run(K8sDemoAppApplication.class, args);
    }

    @GetMapping("/")
    public String hello() {
        String name = rest.getForObject("http://k8s-workshop-name-service", String.class);
        return "Hola " + name;
    }
}
```

## 42: Testing the App

Modify the **skaffold.yaml** file to specify the port to forward to. This is optional, Skaffold will allocate a random port. but for the sake of everyone running through this workshop and having some consistency we specify the port in our Skaffold configuration.

```yaml
- resourceType: service
  resourceName: k8s-workshop-name-service
  port: 80
  localPort: 4504
```

Deploy the apps using Skaffold

`skaffold dev --port-forward`

- This should deploy both the k8s-demo-app and the name-service app

`kubectl get all`

Your output will be similar to:

```
NAME                                             READY   STATUS    RESTARTS   AGE
pod/k8s-demo-app-5b957cf66d-w7r9d                1/1     Running   0          172m
pod/k8s-workshop-name-service-79475f456d-4lqgl   1/1     Running   0          173m

NAME                                TYPE           CLUSTER-IP       EXTERNAL-IP     PORT(S)        AGE
service/k8s-demo-app                LoadBalancer   10.100.200.102   35.238.231.79   80:30068/TCP   173m
service/k8s-workshop-name-service   ClusterIP      10.100.200.16    <none>          80/TCP         173m

NAME                                        READY   UP-TO-DATE   AVAILABLE   AGE
deployment.apps/k8s-demo-app                1/1     1            1           173m
deployment.apps/k8s-workshop-name-service   1/1     1            1           173m

NAME                                                   DESIRED   CURRENT   READY   AGE
replicaset.apps/k8s-demo-app-5b957cf66d                1         1         1       172m
replicaset.apps/k8s-demo-app-fd497cdfd                 0         0         0       173m
replicaset.apps/k8s-workshop-name-service-79475f456d   1         1         1       173m
```

Because we deployed two services and supplied the `-port-forward` flag, Skaffold will forward two ports

```
Port forwarding service/k8s-demo-app in namespace lab-workshop-1-w01-s002, remote port 80 -> address 127.0.0.1 port 4503
Port forwarding service/k8s-workshop-name-service in namespace lab-workshop-1-w01-s002, remote port 80 -> address 127.0.0.1 port 4504
```

- Test the name service

`curl localhost:4504`

Hitting the service multiple times will return a different name

- Test the k8s-demo-app which should now make a request to the name-service

`curl localhost:4503`

Making multiple requests should result in different names coming from the name-service

curl localhost:4503
Stop the Skaffold process to clean everything up before moving to the next step

`<ctrl+c>`

## 43: Running The PetClinic App

- The PetClinic app is a popular demo app which leverages several Spring technologies
  - Spring Data (using MySQL)
  - Spring Caching
  - Spring WebMVC

> alt_text

## 44: Deploying PetClinic

- We have a Kustomization that we can use to easily get it up and running

`kustomize build https://github.com/dsyer/docker-services/layers/samples/petclinic?ref=HEAD | kubectl apply -f -`

The above **kustomize build** command may take some time to complete. You can watch the pod status to know once everything is ready.

`watch kubectl get all`

Once the pod is up and running, you can stop the **watch** command.

To exit the watch command

`<ctrl+c>`

Add an ingress rule to expose the service

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: petclinic-app
  labels:
    app: petclinic-app
spec:
  rules:
    - host: petclinic-app-eduk8s-labs-w05-s032.reg-prod-e7decde.tanzu-labs.esp.vmware.com
      http:
        paths:
          - path: "/"
            pathType: Prefix
            backend:
              service:
                name: petclinic-app
                port:
                  number: 80
```

Use the following command to apply the new ingress rule to the cluster

`kubectl apply -f ~/demo/petclinic-ingress.yaml`

Depending on the ingress being used in the Kubernetes cluster you might need to wait for an IP address to be assigned to the ingress. For this lab, if you are using the hosted version you will not see an IP address assigned. If you are running the workshop locally on a Kubernetes cluster like Minikube you will need to wait for an IP address to be assigned.

Use the following command to find the hostname for the ingress rule and optionally watch for an IP address to be assigned to the ingress rule.

`kubectl get ingress -w`

You can open the host name in your browser or click the action below to open the Pet Clinic dashboard in the workshop.

- To use the app you can go to “Find Owners”, add yourself, and add your pets
- All this data will be stored in the MySQL database

To exit the watch command

`<ctrl+c>`

## 45: Dissecting PetClinic

Here’s the **kustomization.yaml** that you just deployed:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - ../../base
  - ../../mysql
namePrefix: petclinic-
transformers:
  - ../../mysql/transformer
  - ../../actuator
  - ../../prometheus
images:
  - name: dsyer/template
    newName: dsyer/petclinic
configMapGenerator:
  - name: env-config
    behavior: merge
    literals:
      - SPRING_CONFIG_LOCATION=classpath:/,file:///config/bindings/mysql/meta/
      - MANAGEMENT_ENDPOINTS_WEB_BASEPATH=/actuator
      - DATABASE=mysql
```

The relative paths **../../\*** are all relative to this file. Clone the repository to look at those: **git clone https://github.com/dsyer/docker-services** and look at **layers/samples/petclinc/kustomization.yaml**.
Clone the repository for the **kustomization.yaml**.

`git clone https://github.com/dsyer/docker-services`

Look at `~/docker-services/layers/samples/petclinic/kustomization.yaml`.

`base`

Important features:

- **base**: a generic **Deployment** and **Service** with a **Pod** listening on port 8080
- **mysql**: a local MySQL **Deployment** and **Service**. Needs a **PersistentVolume** so only works on Kubernetes clusters that have a default volume provider
- **transformers**: patches to the basic application deployment. The patches are generic and could be shared by multiple different applications.
- **env-config**: the base layer uses this **ConfigMap** to expose environment variables for the application container. These entries are used to adapt the PetClinic to the Kubernetes environment.

## 46: Workshop Summary

During this workshop you learned the finer details of how to create, build, run, and debug a basic Spring Boot app on Kubernetes by doing the following:

- Create a basic Spring Boot app
- Build a Docker image for the app
- Push the image to a Docker registry
- Deploy and run the app on Kubernetes
- Test the app using port-forwarding and ingress
- Use skaffold to iterate easily as you work on your app
- Use kustomize to manage configurations across environments
- Externalize application configuration using ConfigMaps
- Use service discovery for app-to-app communication
- Deploy the Spring PetClinic App with MySQL

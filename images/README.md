# LOCI: images

This directory contains a Dockerfile for building a Loci Java layer docker container. Rather than deploying the software as a series of jar's in a tarball, we create a docker container and export that to the control computer.

## Deployment

### Java layer docker

To build a docker container do the following (on an LT development machine, where the loci software repository is installed at /home/dev/src/loci) :

* **cd ~dev/src/loci/images** (i.e. this directory)
* **sudo docker build -f loci_java_layer -t loci_java_layer_image /** Build the docker containder from the **loci_java_layer** file.
* **docker save -o loci_java_layer_image.tar loci_java_layer_image** Save the constructed docker container into the **loci_java_layer_image.tar** tarball.

This saved docker tarball can then be copied to loci1 (the Loci control computer) as follows:

* **scp -C loci_java_layer_image.tar admin@loci1:images**

The docker can then be installed / loaded into the local system as follows:

* **ssh admin@loci1**
* **cd images**
* **sudo docker load -i loci_java_layer_image.tar**

You now need to install the Loci Java layer config files before starting the docker.


### Java layer config files

To generate a tarball containing the Loci Java layer config files, on an LT development machine , where the loci software repository is installed at /home/dev/src/loci, do the following:

* **cd ~dev/src/loci/scripts/**
* **./loci_create_config_tarball** This currently uses the docker.<config file> version of the config files.

The config tarball ends up at: ~dev/public_html/loci/deployment/loci_config_deployment.tar.gz . Copy the config tarball to loci1:

* **scp -C ~dev/public_html/loci/deployment/loci_config_deployment.tar.gz admin@loci1:images**

Then install the config tarball as follows:

* **ssh admin@loci1**
* **cd /**
* **sudo tar xvfz /home/admin/images/loci_config_deployment.tar.gz** 
* **sudo chown root:root /** This fixes root's permisions.

### Starting the Loci Java layer

The Loci Java layer can then be started as follows:

* **sudo docker run -p 7679:7679 -p 8473:8473 --mount type=bind,src=/icc,dst=/icc --mount type=bind,src=/data,dst=/data -it loci_java_layer_image**

For this to work the Loci Java layer config files need to have been installed under **/icc** first. 


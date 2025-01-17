# LOCI

Source code for the Liverpool Telescope / Java for the Liverpool Optical Compact Imager (LOCI) instrument.

This is a simple imaging camera (Andor Ikon-L 936 CCD camera) ,with a Starlight Express filter wheel. It is designed to be used on both the Liverpool Telescope (LT) and the New Robotic Telescope (NRT), and the software infrastructure is designed to enable as much common code between the two installations as possible.

This repository currently contains the upper robotic Java layer that interfaces with the Liverpool Telescope RCS.

The lower-level CCD control software API is provided by the NRT, and their repository is currently located here:

https://gitlab.services.newrobotictelescope.org/nrt-crew/ljmu/loci-ctrl/

Current details for the test system in the lab are in LT wikiword: LOCIControlComputer

## Directory Structure

* **images** This contains a Dockerfile for building a Loci Java layer docker container. Rather than deploying the software as a series of jar's in a tarball, we create a docker container and export that to the control computer.
* **java** This contains the source code for the Liverpool Telescope robotic layer, which receives commands from the LT robotic control system.
* **scripts** This contains some shell scripts. The **loci_create_config_tarball** creates a configuration file tarball, which can be deployed to the control computer to provide configuration files for the Loci Java layer docker container.

The **Makefile.common** file is included in Makefile's to provide common root directory information.

The **docker-compose.yaml** file is a docker compose file. This can build a docker container from a machine with the LT development environment installed, and then run it up. As we don't intend to install a full LT development environment on the Loci control computer this is currently useless outside ARI testing. We really need a better dockerfile that builds the loci control software and all it's dependencies from scratch inside a docker - this has not been developed at the present time.

## Dependencies / Prerequisites

* The ngat repo/package must be installed: https://github.com/LivTel/ngat .  The specific sub-packages required can be derived from the images/loci_java_layer dockerfile, currently:
  * ngat_util_logging.jar
  * ngat_util.jar
  * ngat_phase2.jar
  * ngat_net.jar
  * ngat_math.jar
  * ngat_fits.jar
  * ngat_flask.jar
  * ngat_message_iss_inst.jar
  * ngat_message_inst_dp.jar
 * ngat_message_base.jar
* The software can only be built from within an LT development environment
* You need a json handling library, we currently use org.json : json-20240303.jar . This is available here: https://mvnrepository.com/artifact/org.json/json/20240303

* The ics_gui package should be installed on the machine you wish to control the instrument from (in it's LT form): https://github.com/LivTel/ics_gui

## Deployment

We are trying to use docker to run the Java layer for Loci. Rather than using docker-compose on the control computer itself, we are currently building a docker container on an LT development machine and copying it (say via scp over the VPN) to the instrument machine on the TLAN and loading it onto docker here. The relevant configuration files also get put in a tarball and installed on the instrument machine. See the [images](images) directory for details.

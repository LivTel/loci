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


## Command line test tools

There are command line test tools that can be invoked to control the lower level CCD and filter wheel Flask API's. These are embedded into the Java layer docker container. As there is no software directly installed on the control computer these cannot be directly invoked from the control computer command line, however we can use the Java layer container, invoked with a different entry point (/bin/bash in this case), which will allow us to invoke the command line tools (which has the Java JVM, CLASSPATH etc already set up) as follows:


* ssh into the loci control computer.
* **sudo docker run --entrypoint /bin/bash -it loci_java_layer_image**
This will run up a bash sheel inside a loci_java_layer docker container instance. The actual command can then be invoked as follows:

* **java ngat.loci.ccd.GetTemperatureCommand 150.204.240.135 5100**

This gets the CCD temperature. Here the IP address is the one assigned to the loci control computer on the ARI network, when the computer is on the TLAN it's TLAN IP address should be used instead.
The CCD command line programs are as shown below. Running the command without any arguments ususally gives some information on the command line arguments needed.

* **java ngat.loci.ccd.GetTemperatureCommand ${hostname} 5100** Get the CCD temperature
* **java ngat.loci.ccd.SetTemperatureCommand ${hostname} 5100 -14** Set the CCD temperature (in degrees centigrade)
* **java ngat.loci.ccd.SetCoolingCommand ${hostname} 5100 true** Turn the Cooler on.
* **java ngat.loci.ccd.AbortExposureCommand ${hostname} 5100** Abort a running Bias/Dark/Exposure
* **java ngat.loci.ccd.SetImageDimensionsCommand ${hostname} 5100 2 2** Set the binning to 2x2. You can also set a sub-image window as follows:
  * **java ngat.loci.ccd.SetImageDimensionsCommand <hostname> <port number> <xbin> <ybin> [<startX> <startY> <endX> <endY>]**
* **java ngat.loci.ccd.TakeBiasFrameCommand ${hostname} 5100 start** Take a Bias frame ('start' a new MULTBIAS). The command will default to 'start' if no argument is supplied, you can  call:
  * **java ngat.loci.ccd.TakeBiasFrameCommand ${hostname} 5100 next** to take subsequent bias frames in the same multbias.
* **java ngat.loci.ccd.TakeDarkFrameCommand ${hostname} 5100 10.0 start** Take a 10 second dark frame ('start' a new MULTDARK). The command will default to 'start' if no argument is supplied, you can use 'next' for subsequent frames in a MULTDARK.
* **java ngat.loci.ccd.TakeExposureCommand ${hostname} 5100 10.0** Take a 10 second exposure. There are more options as follows:
  * **java ngat.loci.ccd.TakeExposureCommand <hostname> <port number> <exposurelength s> [<multrun:start|next> <exposure type>]** where <exposure type> can be one of: "exposure", "sky-flat", "acquire",  "standard"
* **java ngat.loci.ccd.GetCameraStatusCommand ${hostname} 5100** Get the Camera Status (DRV_IDLE/DRV_ACQUIRING).
* **java ngat.loci.ccd.GetExposureProgressCommand ${hostname} 5100** Get the elapsed/remaining exposure length.
* **java ngat.loci.ccd.ClearHeaderKeywordsCommand ${hostname} 5100** Clear the user-defined FITS header list.
* **java ngat.loci.ccd.SetHeaderKeywordCommand ${hostname} 5100 TEST1 1.23 float "Test FITS Header" numeric** Add a user-defined FITS header (run without options to get more information on the command line arguments).

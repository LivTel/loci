# Makefile
include Makefile.common
include ../../../Makefile.common
include ../../../../Makefile.common

MAKE 		= make
PACKAGEDIR	= ngat/loci/
BINDIR 		= $(LOCI_BIN_HOME)/java/$(PACKAGEDIR)
CONFIG_BINDIR 	= $(LOCI_BIN_HOME)/java
SCRIPT_BINDIR 	= $(LOCI_BIN_HOME)/java
SRCS 		= $(MAIN_SRCS) $(IMPL_SRCS)
MAIN_SRCS 	= LociConstants.java \
		Loci.java LociStatus.java LociTCPClientConnectionThread.java \
		LociTCPServerConnectionThread.java LociTCPServer.java LociREBOOTQuitThread.java
IMPL_SRCS = $(BASE_IMPL_SRCS) $(CALIBRATE_IMPL_SRCS) $(EXPOSE_IMPL_SRCS) $(INTERRUPT_IMPL_SRCS) $(SETUP_IMPL_SRCS)
BASE_IMPL_SRCS		= CommandImplementation.java JMSCommandImplementation.java UnknownCommandImplementation.java \
			  HardwareImplementation.java
CALIBRATE_IMPL_SRCS 	= CALIBRATEImplementation.java BIASImplementation.java DARKImplementation.java \
			  MULTBIASImplementation.java MULTDARKImplementation.java TWILIGHT_CALIBRATEImplementation.java
EXPOSE_IMPL_SRCS	= EXPOSEImplementation.java MULTRUNImplementation.java 
INTERRUPT_IMPL_SRCS 	= ABORTImplementation.java GET_STATUSImplementation.java REBOOTImplementation.java

SETUP_IMPL_SRCS		= CONFIGImplementation.java
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)

CONFIG_SRCS	= loci1.loci.net.properties loci1.loci.properties loci1.fits.properties \
		  ltdevx64.loci.net.properties ltdevx64.loci.properties ltdevx64.fits.properties \
		  docker.loci.net.properties docker.loci.properties docker.fits.properties

CONFIG_OBJS	= $(CONFIG_SRCS:%.properties=$(CONFIG_BINDIR)/%.properties)
SCRIPT_SRCS	= 
SCRIPT_OBJS	= $(SCRIPT_SRCS:%=$(SCRIPT_BINDIR)/%)
DIRS 		= ccd filterwheel

top: dirs $(OBJS) config scripts dirs

#
# Java classes
#
$(BINDIR)/%.class:%.java
	$(JAVAC) -sourcepath .:$(LOCI_SRC_HOME)/java \
		-classpath $(LOCI_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) $(JAVAC_VERSION_OPTIONS) -d $(LOCI_BIN_HOME)/java $<
#
# configuration files
#
config: $(CONFIG_OBJS)

$(CONFIG_BINDIR)/%.properties: %.properties
	$(CP) $< $@

#
# run scripts
#
scripts: $(SCRIPT_OBJS)

#
# sub dirs
#
dirs:
	@for i in $(DIRS); \
	do \
		(echo making in $$i...; cd $$i; $(MAKE) ); \
	done;

depend:
	echo no depend in ngat/loci directory.

clean: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS)
	-$(RM) $(RM_OPTIONS) $(JARLIBDIR)/$(JAR_FILE)
	@for i in $(DIRS); \
	do \
		(echo clean in $$i...; cd $$i; $(MAKE) clean); \
	done;

tidy:
	-$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)
	@for i in $(DIRS); \
	do \
		(echo tidy in $$i...; cd $$i; $(MAKE) tidy); \
	done;

backup: tidy checkin
	@for i in $(DIRS); \
	do \
		(echo backup in $$i...; cd $$i; $(MAKE) backup); \
	done;

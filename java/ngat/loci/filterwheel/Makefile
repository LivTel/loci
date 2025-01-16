# Makefile
include ../Makefile.common
include ../../../../../Makefile.common
include	../../../../Makefile.common

PACKAGEDIR	= ngat/loci/filterwheel
BINDIR 		= $(LOCI_BIN_HOME)/java/$(PACKAGEDIR)
SRCS 		= Command.java GetFilterPositionCommand.java GetStatusCommand.java SetFilterPositionCommand.java \
		  SetFilterPositionByNameCommand.java 
OBJS 		= $(SRCS:%.java=$(BINDIR)/%.class)

top: $(OBJS)

$(BINDIR)/%.class:%.java
	$(JAVAC) -sourcepath .:$(LOCI_SRC_HOME)/java \
		-classpath $(LOCI_BIN_HOME)/java:$(CLASSPATH) $(JAVAC_OPTIONS) $(JAVAC_VERSION_OPTIONS) -d $(LOCI_BIN_HOME)/java $<

clean: tidy
	-$(RM) $(RM_OPTIONS) $(OBJS)

tidy:
	-$(RM) $(RM_OPTIONS) $(TIDY_OPTIONS)

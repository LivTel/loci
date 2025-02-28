include ../../../Makefile.common
include ../../Makefile.common
include ../Makefile.common

INCDIR 		= $(LOCI_C_SRC_HOME)/include
TESTDIR 	= test
BINDIR 		= $(LOCI_C_BIN_HOME)/$(TESTDIR)/$(HOSTTYPE)
DOCSDIR 	= $(LOCI_C_DOC_HOME)/$(TESTDIR)

CFLAGS 		= -g -I$(INCDIR) $(ANDOR_CFLAGS)
#-I$(CFITSIOINCDIR) -I$(JNIINCDIR) -I$(JNIMDINCDIR) $(LOG_UDP_CFLAGS)
LDFLAGS		= -L$(LT_LIB_HOME) $(ANDOR_LDFLAGS)
#-l$(LIBNAME) -lcfitsio $(TIMELIB) $(SOCKETLIB) -lm -lc 
DOCFLAGS 	= -static

SRCS 		= test_andor_readout_speeds.c
OBJS 		= $(SRCS:%.c=%.o)
PROGS 		= $(SRCS:%.c=$(BINDIR)/%)
DOCS 		= $(SRCS:%.c=$(DOCSDIR)/%.html)

top: $(PROGS) docs

$(BINDIR)/%: %.o
	$(CC) -o $@ $< -L$(LT_LIB_HOME) $(ANDOR_LDFLAGS)
#-l$(LIBNAME) -lcfitsio $(TIMELIB) $(SOCKETLIB) -lm -lc

#$(BINDIR)/test_andor_readout_speeds: $(BINDIR)/test_andor_readout_speeds.o
#	$(CC) -o $@ $< -L$(LT_LIB_HOME) $(ANDOR_LDFLAGS)

docs: $(DOCS)

$(DOCS): $(SRCS)
	-$(CDOC) -d $(DOCSDIR) -h $(INCDIR) $(DOCFLAGS) $(SRCS)

depend:
	makedepend $(MAKEDEPENDFLAGS) -- $(CFLAGS) $(ANDOR_CFLAGS) -- $(SRCS)

clean:
	$(RM) $(RM_OPTIONS) $(OBJS) $(PROGS) $(TIDY_OPTIONS)

// Loci.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.lang.reflect.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import ngat.net.*;
import ngat.util.*;
import ngat.util.logging.*;
import ngat.message.ISS_INST.*;
import ngat.message.INST_DP.*;

import ngat.loci.ccd.*;

/**
 * This class is the entry point for the Loci (Liverpool Optical Compact Imager) Control System.
 * @author Chris Mottram
 * @version $Revision: Loci.java $
 */
public class Loci
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Logger channel id.
	 */
	public final static String LOGGER_CHANNEL_ID = new String("LOCI");
	/**
	 * Internal constant used when converting temperatures in centigrade to Kelvin.
	 */
	public final static double CENTIGRADE_TO_KELVIN = 273.15;
	/**
	 * The minimum port number to listen for connections on.
	 */
	static final int MINIMUM_PORT_NUMBER = 1025;
	/**
	 * The maximum port number to send ISS commands on.
	 */
	static final int MAXIMUM_PORT_NUMBER = 65535;
	/**
	 * The server class that listens for connections.
	 */
	private LociTCPServer server = null;
	/**
	 * The server class that listens for Telescope Image Transfer request connections.
	 */
	private TitServer titServer = null;
	/**
	 * Status object.
	 */
	private LociStatus status = null;
	/**
	 * This hashtable holds the map between COMMAND sub-class names and their implementations, which
	 * are stored as the Hashtable data values as class objects of sub-classes of CommandImplementation.
	 * When Loci gets a COMMAND from a client it can query this Hashtable to find it's implementation class.
	 */
	private Hashtable implementationList = null;
	/**
	 * Command line argument. The level of logging to perform in Loci.
	 */
	private int logLevel = 0;
	/**
	 * The port number to listen for connections from clients on.
	 */
	private int lociPortNumber = 0;
	/**
	 * The ip address of the machine the ISS is running on, to send ISS commands to.
	 */
	private InetAddress issAddress = null;
	/**
	 * The port number to send iss commands to.
	 */
	private int issPortNumber = 0;
	/**
	 * The ip address of the machine the DP(RT) is running on, to send Data Pipeline (Real Time) commands to.
	 */
	private InetAddress dprtAddress = null;
	/**
	 * The port number to send DP(RT) commands to.
	 */
	private int dprtPortNumber = 0;
	/**
	 * The port number to listen for Telescope Image Transfer requests.
	 */
	private int titPortNumber = 0;
	/**
	 * The logging logger.
	 */
	protected Logger logLogger = null;
	/**
	 * The error logger.
	 */
	protected Logger errorLogger = null;


	/**
	 * Create an instance of the LociStatus object.
	 * @see #status
	 * @see ngat.loci.LociStatus
	 */
	private void initStatus()
	{
		// create status object
		status = new LociStatus();
	}
	
	/**
	 * Method to initialise Loci. Here we assume initStatus() has been called before this method,
	 * to create the LociStatus object instance.
	 * <ul>
	 * <li>Load the Loci properties file into ithe previously created LociStatus instance.
	 * <li>We initialise the loggers (initLoggers).
	 * <li>We set the log level (setLogLevel).
	 * <li>We initialise the list of commands and their implementation classes (initImplementationList)
	 * <li>We initialise various port numbers from the properties file.
	 * <li>We initialise the IP address of the ISS (Instrument Support Service) from the properties file.
	 * <li>We initialise the IP address of the DpRt (Data Pipeline (Real Time)) from the properties file.
	 * <li>We initialise various acknowledge times (timeouts associated with the receiving of commands
	 *     over network sockets) from the properties file.
	 * <li>
	 * </ul>
	 * @see #initLoggers
	 * @see #setLogLevel
	 * @see #initImplementationList
	 * @see #status
	 * @see #lociPortNumber
	 * @see #issPortNumber
	 * @see #dprtPortNumber
	 * @see #titPortNumber
	 * @see #issAddress
	 * @see #dprtAddress
	 * @see ngat.loci.LociStatus
	 * @see ngat.loci.LociTCPServerConnectionThread#setDefaultAcknowledgeTime
	 * @see ngat.loci.LociTCPServerConnectionThread#setMinAcknowledgeTime
	 * @throws FileNotFoundException Thrown if the LociStatus.load method fails.
	 * @throws IOException Thrown if the LociStatus.load method fails.
	 * @throws NumberFormatException Thrown if various port numbers cannot be parsed from the config file.
	 * @throws Exception Thrown if an error occurs.
	 */
	private void init() throws FileNotFoundException,IOException,
		NumberFormatException,Exception
	{
		int time;

		System.out.println(this.getClass().getName()+":init:Started.");
		// load loci properties into the status object instance
		try
		{
			System.out.println(this.getClass().getName()+":init:Loading status properties.");
			status.load();
		}
		catch(FileNotFoundException e)
		{
			error(this.getClass().getName()+":init:loading properties:",e);
			throw e;
		}
		catch(IOException e)
		{
			error(this.getClass().getName()+":init:loading properties:",e);
			throw e;
		}
	// Logging
		System.out.println(this.getClass().getName()+":init:Initialising loggers.");
		initLoggers();
	// initialise sub-system loggers, after creating status, hardware control objects
		setLogLevel(logLevel);
	// Create and initialise the implementationList
		initImplementationList();
	// initialise port numbers from properties file/ command line arguments
		try
		{
			lociPortNumber = status.getPropertyInteger("loci.net.loci.port_number");
			issPortNumber = status.getPropertyInteger("loci.net.iss.port_number");
			dprtPortNumber = status.getPropertyInteger("loci.net.dprt.port_number");
			titPortNumber = status.getPropertyInteger("loci.net.tit.port_number");
		}
		catch(NumberFormatException e)
		{
			error(this.getClass().getName()+":init:initialsing port number:",e);
			throw e;
		}
	// initialise address's from properties file
		try
		{
			issAddress = InetAddress.getByName(status.getProperty("loci.net.iss.address"));
			dprtAddress = InetAddress.getByName(status.getProperty("dprt.net.dprt.address"));
		}
		catch(UnknownHostException e)
		{
			error(this.getClass().getName()+":illegal internet address:",e);
			throw e;
		}
	// initialise default connection response times from properties file
		try
		{
			time = status.getPropertyInteger("loci.server_connection.default.acknowledge_time");
			LociTCPServerConnectionThread.setDefaultAcknowledgeTime(time);
			time = status.getPropertyInteger("loci.server_connection.min.acknowledge_time");
			LociTCPServerConnectionThread.setMinAcknowledgeTime(time);
		}
		catch(NumberFormatException e)
		{
			error(this.getClass().getName()+":init:initialsing server connection thread times:",e);
			// don't throw the error - failing to get this property is not 'vital' to Loci.
		}		
	}

	/**
	 * Initialise log handlers. Called from init only, not re-configured on a REDATUM level reboot.
	 * @see #LOGGER_CHANNEL_ID
	 * @see #init
	 * @see #initLogHandlers
	 * @see #copyLogHandlers
	 * @see #errorLogger
	 * @see #logLogger
	 */
	protected void initLoggers()
	{
	// errorLogger setup
		errorLogger = LogManager.getLogger("error");
		errorLogger.setChannelID(LOGGER_CHANNEL_ID+"-ERROR");
		initLogHandlers(errorLogger);
		errorLogger.setLogLevel(Logging.ALL);
	// ngat.net error loggers
		copyLogHandlers(errorLogger,LogManager.getLogger("ngat.net.TCPServer"),null,Logging.ALL);
		copyLogHandlers(errorLogger,LogManager.getLogger("ngat.net.TCPServerConnectionThread"),null,
				Logging.ALL);
		copyLogHandlers(errorLogger,LogManager.getLogger("ngat.net.TCPClientConnectionThreadMA"),null,
				Logging.ALL);
	// logLogger setup
		logLogger = LogManager.getLogger("log");
		logLogger.setChannelID(LOGGER_CHANNEL_ID);
		initLogHandlers(logLogger);
		logLogger.setLogLevel(status.getLogLevel());
	// library logging loggers
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.net.TitServer"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.flask.EndPoint"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.Command"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.AbortExposureCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.GetCameraStatusCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.GetExposureProgressCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.GetTemperatureCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.SetCoolingCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.SetHeaderKeywordCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.SetImageDimensionsCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.SetTemperatureCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.TakeBiasFrameCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.TakeDarkFrameCommand"),null,Logging.ALL);
		copyLogHandlers(logLogger,LogManager.getLogger("ngat.loci.ccd.TakeExposureCommand"),null,Logging.ALL);
	}
	
	/**
	 * Method to create and add all the handlers for the specified logger.
	 * These handlers are in the status properties:
	 * "loci.log."+l.getName()+".handler."+index+".name" retrieves the relevant class name
	 * for each handler.
	 * @param l The logger.
	 * @see #initFileLogHandler
	 * @see #initConsoleLogHandler
	 * @see #initDatagramLogHandler
	 */
	protected void initLogHandlers(Logger l)
	{
		LogHandler handler = null;
		String handlerName = null;
		int index = 0;

		do
		{
			handlerName = status.getProperty("loci.log."+l.getName()+".handler."+index+".name");
			if(handlerName != null)
			{
				try
				{
					handler = null;
					if(handlerName.equals("ngat.util.logging.FileLogHandler"))
					{
						handler = initFileLogHandler(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.ConsoleLogHandler"))
					{
						handler = initConsoleLogHandler(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.MulticastLogHandler"))
					{
						handler = initMulticastLogHandler(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.MulticastLogRelay"))
					{
						handler = initMulticastLogRelay(l,index);
					}
					else if(handlerName.equals("ngat.util.logging.DatagramLogHandler"))
					{
						handler = initDatagramLogHandler(l,index);
					}
					else
					{
						error("initLogHandlers:Unknown handler:"+handlerName);
					}
					if(handler != null)
					{
						handler.setLogLevel(Logging.ALL);
						l.addHandler(handler);
					}
				}
				catch(Exception e)
				{
					error("initLogHandlers:Adding Handler failed:",e);
				}
				index++;
			}
		}
		while(handlerName != null);
	}

	/**
	 * Routine to add a FileLogHandler to the specified logger.
	 * This method expects either 3 or 6 constructor parameters to be in the status properties.
	 * If there are 6 parameters, we create a record limited file log handler with parameters:
	 * <ul>
	 * <li><b>param.0</b> is the filename.
	 * <li><b>param.1</b> is the formatter class name.
	 * <li><b>param.2</b> is the record limit in each file.
	 * <li><b>param.3</b> is the start index for file suffixes.
	 * <li><b>param.4</b> is the end index for file suffixes.
	 * <li><b>param.5</b> is a boolean saying whether to append to files.
	 * </ul>
	 * If there are 3 parameters, we create a time period file log handler with parameters:
	 * <ul>
	 * <li><b>param.0</b> is the filename.
	 * <li><b>param.1</b> is the formatter class name.
	 * <li><b>param.2</b> is the time period, either 'HOURLY_ROTATION','DAILY_ROTATION' or 'WEEKLY_ROTATION'.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception NumberFormatException Thrown if the numeric parameters in the properties
	 * 	file are not valid numbers.
	 * @exception FileNotFoundException Thrown if the specified filename is not valid in some way.
	 * @see #status
	 * @see #initLogFormatter
	 * @see LociStatus#getProperty
	 * @see LociStatus#getPropertyInteger
	 * @see LociStatus#getPropertyBoolean
	 * @see LociStatus#propertyContainsKey
	 * @see LociStatus#getPropertyLogHandlerTimePeriod
	 */
	protected LogHandler initFileLogHandler(Logger l,int index) throws NumberFormatException,
		FileNotFoundException
	{
		LogFormatter formatter = null;
		LogHandler handler = null;
		String fileName;
		int recordLimit,fileStart,fileLimit,timePeriod;
		boolean append;

		fileName = status.getProperty("loci.log."+l.getName()+".handler."+index+".param.0");
		formatter = initLogFormatter("loci.log."+l.getName()+".handler."+index+".param.1");
		// if we have more then 3 parameters, we are using a recordLimit FileLogHandler
		// rather than a time period log handler.
		if(status.propertyContainsKey("loci.log."+l.getName()+".handler."+index+".param.3"))
		{
			recordLimit = status.getPropertyInteger("loci.log."+l.getName()+".handler."+index+
								".param.2");
			fileStart = status.getPropertyInteger("loci.log."+l.getName()+".handler."+index+".param.3");
			fileLimit = status.getPropertyInteger("loci.log."+l.getName()+".handler."+index+".param.4");
			append = status.getPropertyBoolean("loci.log."+l.getName()+".handler."+index+".param.5");
			handler = new FileLogHandler(fileName,formatter,recordLimit,fileStart,fileLimit,append);
		}
		else
		{
			// This is a time period log handler.
			timePeriod = status.getPropertyLogHandlerTimePeriod("loci.log."+l.getName()+".handler."+
									    index+".param.2");
			handler = new FileLogHandler(fileName,formatter,timePeriod);
		}
		return handler;
	}

	/**
	 * Routine to add a MulticastLogHandler to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the multicast group name i.e. "228.0.0.1".
	 * <li>param.1 is the port number i.e. 5000.
	 * <li>param.2 is the formatter class name.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception IOException Thrown if the multicast socket cannot be created for some reason.
	 */
	protected LogHandler initMulticastLogHandler(Logger l,int index) throws IOException
	{
		LogFormatter formatter = null;
		LogHandler handler = null;
		String groupName = null;
		int portNumber;

		groupName = status.getProperty("loci.log."+l.getName()+".handler."+index+".param.0");
		portNumber = status.getPropertyInteger("loci.log."+l.getName()+".handler."+index+".param.1");
		formatter = initLogFormatter("loci.log."+l.getName()+".handler."+index+".param.2");
		handler = new MulticastLogHandler(groupName,portNumber,formatter);
		return handler;
	}

	/**
	 * Routine to add a MulticastLogRelay to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the multicast group name i.e. "228.0.0.1".
	 * <li>param.1 is the port number i.e. 5000.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception IOException Thrown if the multicast socket cannot be created for some reason.
	 */
	protected LogHandler initMulticastLogRelay(Logger l,int index) throws IOException
	{
		LogHandler handler = null;
		String groupName = null;
		int portNumber;

		groupName = status.getProperty("loci.log."+l.getName()+".handler."+index+".param.0");
		portNumber = status.getPropertyInteger("loci.log."+l.getName()+".handler."+index+".param.1");
		handler = new MulticastLogRelay(groupName,portNumber);
		return handler;
	}

	/**
	 * Routine to add a DatagramLogHandler to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the hostname i.e. "ltproxy".
	 * <li>param.1 is the port number i.e. 2371.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of the relevant class is returned, if no exception occurs.
	 * @exception IOException Thrown if the multicast socket cannot be created for some reason.
	 */
	protected LogHandler initDatagramLogHandler(Logger l,int index) throws IOException
	{
		LogHandler handler = null;
		String hostname = null;
		int portNumber;

		hostname = status.getProperty("loci.log."+l.getName()+".handler."+index+".param.0");
		portNumber = status.getPropertyInteger("loci.log."+l.getName()+".handler."+index+".param.1");
		handler = new DatagramLogHandler(hostname,portNumber);
		return handler;
	}

	/**
	 * Routine to add a ConsoleLogHandler to the specified logger.
	 * The parameters to the constructor are stored in the status properties:
	 * <ul>
	 * <li>param.0 is the formatter class name.
	 * </ul>
	 * @param l The logger to add the handler to.
	 * @param index The index in the property file of the handler we are adding.
	 * @return A LogHandler of class FileLogHandler is returned, if no exception occurs.
	 */
	protected LogHandler initConsoleLogHandler(Logger l,int index)
	{
		LogFormatter formatter = null;
		LogHandler handler = null;

		formatter = initLogFormatter("loci.log."+l.getName()+".handler."+index+".param.0");
		handler = new ConsoleLogHandler(formatter);
		return handler;
	}

	/**
	 * Method to create an instance of a LogFormatter, given a property name
	 * to retrieve it's details from. If the property does not exist, or the class does not exist
	 * or an instance cannot be instansiated we try to return a ngat.util.logging.BogstanLogFormatter.
	 * @param propertyName A property name, present in the status's properties, 
	 * 	which has a value of a valid LogFormatter sub-class name. i.e.
	 * 	<pre>loci.log.log.handler.0.param.1 =ngat.util.logging.BogstanLogFormatter</pre>
	 * @return An instance of LogFormatter is returned.
	 */
	protected LogFormatter initLogFormatter(String propertyName)
	{
		LogFormatter formatter = null;
		String formatterName = null;
		Class formatterClass = null;

		formatterName = status.getProperty(propertyName);
		if(formatterName == null)
		{
			error("initLogFormatter:NULL formatter for:"+propertyName);
			formatterName = "ngat.util.logging.BogstanLogFormatter";
		}
		try
		{
			formatterClass = Class.forName(formatterName);
		}
		catch(ClassNotFoundException e)
		{
			error("initLogFormatter:Unknown class formatter:"+formatterName+
				" from property "+propertyName);
			formatterClass = BogstanLogFormatter.class;
		}
		try
		{
			formatter = (LogFormatter)formatterClass.newInstance();
		}
		catch(Exception e)
		{
			error("initLogFormatter:Cannot create instance of formatter:"+formatterName+
				" from property "+propertyName);
			formatter = (LogFormatter)new BogstanLogFormatter();
		}
	// set better date format if formatter allows this.
	// Note we really need LogFormatter to generically allow us to do this
		if(formatter instanceof BogstanLogFormatter)
		{
			BogstanLogFormatter blf = (BogstanLogFormatter)formatter;

			blf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		}
		if(formatter instanceof SimpleLogFormatter)
		{
			SimpleLogFormatter slf = (SimpleLogFormatter)formatter;

			slf.setDateFormat(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS z"));
		}
		return formatter;
	}

	/**
	 * Method to copy handlers from one logger to another. The outputLogger's channel ID is also
	 * copied from the input logger.
	 * @param inputLogger The logger to copy handlers from.
	 * @param outputLogger The logger to copy handlers to.
	 * @param lf The log filter to apply to the output logger. If this is null, the filter is not set.
	 * @param logLevel The log level to set the logger to filter against.
	 */
	protected void copyLogHandlers(Logger inputLogger,Logger outputLogger,LogFilter lf,int logLevel)
	{
		LogHandler handlerList[] = null;
		LogHandler handler = null;

		handlerList = inputLogger.getHandlers();
		for(int i = 0; i < handlerList.length; i++)
		{
			handler = handlerList[i];
			outputLogger.addHandler(handler);
		}
		outputLogger.setLogLevel(inputLogger.getLogLevel());
		if(lf != null)
			outputLogger.setFilter(lf);
		outputLogger.setChannelID(inputLogger.getChannelID());
		outputLogger.setLogLevel(logLevel);
	}

	/**
	 * This is the re-initialisation routine. This is called on a REDATUM level reboot, and
	 * does some of the operations in the init routine. It re-loads the Loci configuration
	 * files, but NOT the network one. 
	 * It re-initialises default connection response times from properties file.
	 * The init method must be kept up to date with respect to this method.
	 * @exception FileNotFoundException Thrown if the property file cannot be found.
	 * @exception IOException Thrown if the property file cannot be accessed and the properties cannot
	 * 	be loaded for some reason.
	 * @exception Exception Thrown from FitsFilename.initialise, if the directory listing failed.
	 * @see #status
	 * @see #init
	 * @see #setLogLevel
	 */
	public void reInit() throws FileNotFoundException,IOException,NumberFormatException,Exception
	{
		int time;

	// reload properties into the status object
		try
		{
			status.reload();
		}
		catch(FileNotFoundException e)
		{
			error(this.getClass().getName()+":reinit:loading properties:",e);
			throw e;
		}
		catch(IOException e)
		{
			error(this.getClass().getName()+":reinit:loading properties:",e);
			throw e;
		}
	// don't change errorLogger to files defined in loaded properties
	// don't change logLogger to files defined in loaded properties
	// initialise sub-system loggers
		setLogLevel(logLevel);
	// initialise default connection response times from properties file
		try
		{
			time = status.getPropertyInteger("loci.server_connection.default_acknowledge_time");
			LociTCPServerConnectionThread.setDefaultAcknowledgeTime(time);
			time = status.getPropertyInteger("loci.server_connection.min_acknowledge_time");
			LociTCPServerConnectionThread.setMinAcknowledgeTime(time);
		}
		catch(NumberFormatException e)
		{
			error(this.getClass().getName()+":reinit:initialsing server connection thread times:",e);
			// don't throw the error - failing to get this property is not 'vital' to Loci.
		}
	}

	/**
	 * This method creates the implementationList, and fills it with Class objects of sub-classes
	 * of CommandImplementation. The command implementation namess are retrieved from the Loci property files,
	 * using keys of the form <b>loci.command.implementation.&lt;<i>N</i>&gt;</b>, where <i>N</i> is
	 * an integer is incremented. It puts the class object reference in the Hashtable with the 
	 * results of it's getImplementString static method
	 * as the key. If an implementation object class fails to be put in the hashtable for some reason
	 * it ignores it and continues for the next object in the list.
	 * @see #implementationList
	 * @see CommandImplementation#getImplementString
	 */
	private void initImplementationList()
	{
		Class cl = null;
		Class oldClass = null;
		Method method = null;
		Class methodClassParameterList[] = {};
		Object methodParameterList[] = {};
		String implementString = null;
		String className = null;
		int index;
		boolean done;

		implementationList = new Hashtable();
		index = 0;
		done = false;
		while(done == false)
		{
			className = status.getProperty("loci.command.implementation."+index);
			if(className != null)
			{
				log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
				    ":initImplementationList:index "+index+" contains class:"+className);
				try
				{
				// get Class object associated with class name
					cl = Class.forName(className);
				// get method object associated with getImplementString method of cl.
					method = cl.getDeclaredMethod("getImplementString",methodClassParameterList);
				// invoke getImplementString class method to get ngat.message class name it implements
					implementString = (String)method.invoke(null,methodParameterList);
					log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+
					    ":initImplementationList:class "+className+
					    " implements command "+implementString);
				// put key and class into implementationList
					oldClass = (Class)implementationList.put(implementString,cl);
					if(oldClass != null)// the put returned another class with the same key.
					{
						error(this.getClass().getName()+":initImplementationList:Classes "+
							oldClass.getName()+" and "+cl.getName()+
							" both implement command:"+implementString);
					}
				}
				catch(ClassNotFoundException e)//Class.forName exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						className+":ClassNotFoundException:",e);
					// keep trying for next implementation in the list
				}
				catch(NoSuchMethodException e)//Class.getDeclaredMethod exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						className+":NoSuchMethodException:",e);
					// keep trying for next implementation in the list
				}
				catch(SecurityException e)//Class.getDeclaredMethod exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						className+":SecurityException:",e);
					// keep trying for next implementation in the list
				}
				catch(NullPointerException e)// Hashtable.put exception - null key.
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+" implement string is null?:",e);
					// keep trying for next implementation in the list
				}
				catch(IllegalAccessException e)// Method.invoke exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+":IllegalAccessException:",e);
					// keep trying for next implementation in the list
				}
				catch(IllegalArgumentException e)// Method.invoke exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+":IllegalArgumentException:",e);
					// keep trying for next implementation in the list
				}
				catch(InvocationTargetException e)// Method.invoke exception
				{
					error(this.getClass().getName()+":initImplementationList:Class "+
						cl.getName()+":InvocationTargetException:",e);
					// keep trying for next implementation in the list
				}
			// try next class name in list
				index++;
			}
			else
				done = true;
		}// end while not done
		log(Logging.VERBOSITY_VERBOSE,this.getClass().getName()+":initImplementationList:finished at index "+
		    index+".");
	}

	/**
	 * Method to initialise the CCD Flask API. This allows us to set the CCD temperature, and turn the 
	 * cooling on and off.
	 * @exception Exception Thrown if the ccd flask API configuration properties cannot be retrieved.
	 * @see LociStatus#getProperty
	 * @see LociStatus#getPropertyInteger
	 * @see ngat.loci.ccd.SetTemperatureCommand
	 * @see ngat.loci.ccd.SetCoolingCommand
	 */
	public void initCCDController() throws Exception
	{
		SetTemperatureCommand setTemperatureCommand = null;
		SetCoolingCommand setCoolingCommand = null;
		String ccdFlaskHostname = null;
		int ccdFlaskPortNumber;
		int targetTemperature;
		boolean enableCooling;
		
		log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":initCCDController:Started.");
		// get CCD Flask API connection data
		ccdFlaskHostname = status.getProperty("loci.flask.ccd.hostname");
		ccdFlaskPortNumber = status.getPropertyInteger("loci.flask.ccd.port_number");
		// get Loci CCD Flask End-point configuration data
		targetTemperature = status.getPropertyInteger("loci.flask.ccd.temperature.target");
		enableCooling = status.getPropertyBoolean("loci.flask.ccd.cooling.enable");
		// set CCD temperature
		setTemperatureCommand = new SetTemperatureCommand();
		setTemperatureCommand.setAddress(ccdFlaskHostname);
		setTemperatureCommand.setPortNumber(ccdFlaskPortNumber);
		setTemperatureCommand.setTemperature(targetTemperature);
		setTemperatureCommand.run();
		if(setTemperatureCommand.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+":initCCDController:Set Temperature failed:",
					    setTemperatureCommand.getRunException());
		}
		log(Logging.VERBOSITY_VERBOSE,"initCCDController:Set Temperature Command Finished with status: "+
			 setTemperatureCommand.getReturnStatus()+
			 " and message:"+setTemperatureCommand.getMessage()+".");
		if(setTemperatureCommand.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":initCCDController:Set Temperature Command failed with status: "+
					    setTemperatureCommand.getReturnStatus()+
					    " and message:"+setTemperatureCommand.getMessage()+".");
		}
		// set whether to turn the cooler on
		setCoolingCommand = new SetCoolingCommand();
		setCoolingCommand.setAddress(ccdFlaskHostname);
		setCoolingCommand.setPortNumber(ccdFlaskPortNumber);
		setCoolingCommand.setCooling(enableCooling);
		setCoolingCommand.run();
		if(setCoolingCommand.getRunException() != null)
		{
			throw new Exception(this.getClass().getName()+":initCCDController:Set Cooling failed:",
					    setCoolingCommand.getRunException());
		}
		log(Logging.VERBOSITY_VERBOSE,"initCCDController:Set Cooling Command Finished with status: "+
			 setCoolingCommand.getReturnStatus()+
			 " and message:"+setCoolingCommand.getMessage()+".");
		if(setCoolingCommand.isReturnStatusSuccess() == false)
		{
			throw new Exception(this.getClass().getName()+
					    ":initCCDController:Set Cooling Command failed with status: "+
					    setCoolingCommand.getReturnStatus()+
					    " and message:"+setCoolingCommand.getMessage()+".");
		}
		log(Logging.VERBOSITY_TERSE,this.getClass().getName()+":initCCDController:Finished.");
	}
	
	/**
	 * This is the run routine. It starts a new server to handle incoming requests, and waits for the
	 * server to terminate.
	 * @see #server
	 * @see #lociPortNumber
	 * @see #titServer
	 * @see #titPortNumber
	 */
	private void run()
	{
		Date nowDate = null;

		log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":run:started.");
		log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":run:creating server on port:"+lociPortNumber);
		server = new LociTCPServer("Loci",lociPortNumber);
		server.setLoci(this);
		server.setPriority(status.getThreadPriorityServer());
		log(Logging.VERBOSITY_VERY_TERSE,this.getClass().getName()+":run:creating Tit server on port:"+titPortNumber);
		titServer = new TitServer("TitServer on port "+titPortNumber,titPortNumber);
		titServer.setPriority(status.getThreadPriorityTIT());
		nowDate = new Date();
		log(Logging.VERBOSITY_VERY_TERSE,
			this.getClass().getName()+":run:server started at:"+nowDate.toString());
		log(Logging.VERBOSITY_VERY_TERSE,
			this.getClass().getName()+":run:server started on port:"+lociPortNumber);
		error(this.getClass().getName()+":run:server started at:"+nowDate.toString());
		error(this.getClass().getName()+":run:server started on port:"+lociPortNumber);
		server.start();
		titServer.start();
		try
		{
			log(Logging.VERBOSITY_VERY_TERSE,
			    this.getClass().getName()+":run:about to wait on server join.");
			server.join();
			log(Logging.VERBOSITY_VERY_TERSE,
			    this.getClass().getName()+":run:server has joined.");
		}
		catch(InterruptedException e)
		{
			error(this.getClass().getName()+":run:",e);
		}
	}

	/**
	 * Routine to be called at the end of execution of Loci to close down communications.
	 * Currently closes LociTCPServer and TitServer.
	 * @param serverConnectionThread An object of class LociTCPServerConnectionThread. The connection thread
	 *        of the command being implemented should be passed in. 
	 * @see LociTCPServer#close
	 * @see #server
	 * @see TitServer#close
	 * @see #titServer
	 */
	public void close(LociTCPServerConnectionThread serverConnectionThread)
	{
		server.close();
		titServer.close();
	}

	/**
	 * Get Socket Server instance.
	 * @return The server instance.
	 * @see #server
	 */
	public LociTCPServer getServer()
	{
		return server;
	}

	/**
	 * Get status instance.
	 * @return The status instance.
	 * @see #status
	 */
	public LociStatus getStatus()
	{
		return status;
	}

	/**
	 * This routine returns an instance of the sub-class of CommandImplementation that
	 * implements the command with class name commandClassName. If an implementation is
	 * not found or an instance cannot be created, an instance of UnknownCommandImplementation is returned instead.
	 * The instance is constructed 
	 * using a null argument constructor, from the Class object stored in the implementationList.
	 * @param commandClassName The class-name of a COMMAND sub-class.
	 * @return A new instance of a sub-class of CommandImplementation that implements the 
	 * 	command, or an instance of UnknownCommandImplementation.
	 */
	public JMSCommandImplementation getImplementation(String commandClassName)
	{
		JMSCommandImplementation unknownCommandImplementation = new UnknownCommandImplementation();
		JMSCommandImplementation object = null;
		Class cl = null;

		cl = (Class)implementationList.get(commandClassName);
		if(cl != null)
		{
			try
			{
				object = (JMSCommandImplementation)cl.newInstance();
			}
			catch(InstantiationException e)//Class.newInstance exception
			{
				error(this.getClass().getName()+":getImplementation:Class "+
					cl.getName()+":InstantiationException:",e);
				object = null;
			}
			catch(IllegalAccessException e)//Class.newInstance exception
			{
				error(this.getClass().getName()+":getImplementation:Class "+
					cl.getName()+":IllegalAccessException:",e);
				object = null;
			}
		}// end if found class
		if(object != null)
			return object;
		else
			return unknownCommandImplementation;
	}

	/**
	 * Method to set the level of logging filtered. The status, log and error loggers have their filters set.
	 * @param level An integer, used as an absolute value (0..5).
	 * @see #status
	 * @see #logLogger
	 * @see #errorLogger
	 * @see ngat.loci.LociStatus#setLogLevel
	 */
	public void setLogLevel(int level)
	{
		status.setLogLevel(level);
		logLogger.setLogLevel(level);
		errorLogger.setLogLevel(level);
	}

	/**
	 * Routine to send a command from the instrument (this application/Loci) to the ISS. The routine
	 * waits until the command's done message has been returned from the ISS and returns this.
	 * If the commandThread is aborted this also stops waiting for the done message to be returned.
	 * @param command The command to send to the ISS.
	 * @param commandThread The thread the passed in command (and this method) is running on.
	 * @return The done message returned from te ISS, or an error message created by this routine
	 * 	if the done was null.
	 * @see #issAddress
	 * @see #issPortNumber
	 * @see #sendISSCommand(INST_TO_ISS,LociTCPServerConnectionThread,boolean)
	 * @see LociTCPClientConnectionThread
	 * @see LociTCPServerConnectionThread#getAbortProcessCommand
	 */
	public INST_TO_ISS_DONE sendISSCommand(INST_TO_ISS command,LociTCPServerConnectionThread commandThread)
	{
		return sendISSCommand(command,commandThread,true);
	}

	/**
	 * Routine to send a command from the instrument (this application/Loci) to the ISS. The routine
	 * waits until the command's done message has been returned from the ISS and returns this.
	 * If checkAbort is set and the commandThread is aborted this also stops waiting for the 
	 * done message to be returned.
	 * @param command The command to send to the ISS.
	 * @param commandThread The thread the passed in command (and this method) is running on.
	 * @param checkAbort A boolean, set to true if we want to check for commandThread aborting.
	 * 	This should be set to false when the command is being sent to the ISS in response
	 * 	to an abort occuring.
	 * @return The done message returned from te ISS, or an error message created by this routine
	 * 	if the done was null.
	 * @see #issAddress
	 * @see #issPortNumber
	 * @see LociTCPClientConnectionThread
	 * @see LociTCPServerConnectionThread#getAbortProcessCommand
	 */
	public INST_TO_ISS_DONE sendISSCommand(INST_TO_ISS command,LociTCPServerConnectionThread commandThread,
					       boolean checkAbort)
	{
		LociTCPClientConnectionThread thread = null;
		INST_TO_ISS_DONE done = null;
		boolean finished = false;

		log(Logging.VERBOSITY_VERY_TERSE,
		    this.getClass().getName()+":sendISSCommand:"+command.getClass().getName());
		thread = new LociTCPClientConnectionThread(issAddress,issPortNumber,command,commandThread);
		thread.setLoci(this);
		thread.start();
		finished = false;
		while(finished == false)
		{
			try
			{
				thread.join(100);// wait 100 millis for the thread to finish
			}
			catch(InterruptedException e)
			{
				error("run:join interrupted:",e);
			}
		// If the thread has finished so has this loop
			finished = (thread.isAlive() == false);
		// check if the thread has been aborted, if checkAbort has been set.
			if(checkAbort)
			{
			// If the commandThread has been aborted, stop processing this thread
				if(commandThread.getAbortProcessCommand())
					finished = true;
			}
		}
		done = (INST_TO_ISS_DONE)thread.getDone();
		if(done == null)
		{
			// one reason the done is null is if we escaped from the loop
			// because the Loci server thread was aborted.
			if(commandThread.getAbortProcessCommand())
			{
				done = new INST_TO_ISS_DONE(command.getId());
				error(this.getClass().getName()+":sendISSCommand:"+
					command.getClass().getName()+":Server thread Aborted");
				done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+1);
				done.setErrorString("sendISSCommand:Server thread Aborted:"+
					command.getClass().getName());
				done.setSuccessful(false);		
			}
			else // a communication failure occured
			{
				done = new INST_TO_ISS_DONE(command.getId());
				error(this.getClass().getName()+":sendISSCommand:"+
					command.getClass().getName()+":Getting Done failed");
				done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2);
				done.setErrorString("sendISSCommand:Getting Done failed:"+
					command.getClass().getName());
				done.setSuccessful(false);
			}
		}
		log(Logging.VERBOSITY_TERSE,
			"Done:"+done.getClass().getName()+":successful:"+done.getSuccessful()+
			":error number:"+done.getErrorNum()+":error string:"+done.getErrorString());
		return done;
	}

	/**
	 * Routine to send a command from the instrument (this application/Loci) to the DP(RT). The routine
	 * waits until the command's done message has been returned from the DP(RT) and returns this.
	 * If the commandThread is aborted this also stops waiting for the done message to be returned.
	 * @param command The command to send to the DP(RT).
	 * @param commandThread The thread the passed in command (and this method) is running on.
	 * @return The done message returned from te DP(RT), or an error message created by this routine
	 * 	if the done was null.
	 * @see #dprtAddress
	 * @see #dprtPortNumber
	 * @see LociTCPClientConnectionThread
	 * @see LociTCPServerConnectionThread#getAbortProcessCommand
	 */
	public INST_TO_DP_DONE sendDpRtCommand(INST_TO_DP command,LociTCPServerConnectionThread commandThread)
	{
		LociTCPClientConnectionThread thread = null;
		INST_TO_DP_DONE done = null;
		boolean finished = false;

		log(Logging.VERBOSITY_VERY_TERSE,
		    this.getClass().getName()+":sendDpRtCommand:"+command.getClass().getName());
		thread = new LociTCPClientConnectionThread(dprtAddress,dprtPortNumber,command,commandThread);
		thread.setLoci(this);
		thread.start();
		finished = false;
		while(finished == false)
		{
			try
			{
				thread.join(100);// wait 100 millis for the thread to finish
			}
			catch(InterruptedException e)
			{
				error("run:join interrupted:",e);
			}
		// If the thread has finished so has this loop
			finished = (thread.isAlive() == false);
		// If the commandThread has been aborted, stop processing this thread
			if(commandThread.getAbortProcessCommand())
				finished = true;
		}
		done = (INST_TO_DP_DONE)thread.getDone();
		if(done == null)
		{
			// one reason the done is null is if we escaped from the loop
			// because the Loci server thread was aborted.
			if(commandThread.getAbortProcessCommand())
			{
				done = new INST_TO_DP_DONE(command.getId());
				error(this.getClass().getName()+":sendDpRtCommand:"+
					command.getClass().getName()+":Server thread Aborted");
				done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+5);
				done.setErrorString("sendDpRtCommand:Server thread Aborted:"+
					command.getClass().getName());
				done.setSuccessful(false);
			}
			else // a communication failure occured
			{
				done = new INST_TO_DP_DONE(command.getId());
				error(this.getClass().getName()+":sendDpRtCommand:"+
					command.getClass().getName()+":Getting Done failed");
				done.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+6);
				done.setErrorString("sendDpRtCommand:Getting Done failed:"+
					command.getClass().getName());
				done.setSuccessful(false);
			}
		}
		log(Logging.VERBOSITY_VERY_TERSE,
			"Done:"+done.getClass().getName()+":successful:"+done.getSuccessful()+
			":error number:"+done.getErrorNum()+":error string:"+done.getErrorString());
		return done;
	}

	/**
	 * Routine to write the string to the relevant logger. If the relevant logger has not been
	 * created yet the error gets written to System.out.
	 * @param level The level of logging this message belongs to.
	 * @param s The string to write.
	 * @see #logLogger
	 */
	public void log(int level,String s)
	{
		if(logLogger != null)
			logLogger.log(level,s);
		else
		{
			if((status.getLogLevel()&level) > 0)
				System.out.println(s);
		}
	}

	/**
	 * Routine to write the string to the relevant logger. If the relevant logger has not been
	 * created yet the error gets written to System.err.
	 * @param s The string to write.
	 * @see #errorLogger
	 */
	public void error(String s)
	{
		if(errorLogger != null)
			errorLogger.log(Logging.VERBOSITY_VERY_TERSE,s);
		else
			System.err.println(s);
	}

	/**
	 * Routine to write the string to the relevant logger. If the relevant logger has not been
	 * created yet the error gets written to System.err.
	 * @param s The string to write.
	 * @param e An exception that caused the error to occur.
	 * @see #errorLogger
	 */
	public void error(String s,Exception e)
	{
		if(errorLogger != null)
		{
			errorLogger.log(Logging.VERBOSITY_VERY_TERSE,s,e);
			errorLogger.dumpStack(Logging.VERBOSITY_VERY_TERSE,e);
		}
		else
		{
			System.err.println(s);
			e.printStackTrace(System.err);
		}
	}

	/**
	 * Help message routine. Prints all command line arguments.
	 */
	public void help()
	{
		System.out.println("Loci Help:");
		System.out.println("Arguments are:");
		System.out.println("\t-l[og] <log level> - Set log level.");
		System.out.println("\t-cfpf|-current_filter_property_filename - Set properties filename to load current filters from.");
		System.out.println("\t-filterpf|-filter_property_filename - Set properties filename to load filter database from.");
		
		System.out.println("\t-fitspf|-fits_property_filename - Set properties filename to load FITS properties from.");

		System.out.println("\t-lpf|-loci_property_filename - Set properties filename to load the main Loci instrument properties from.");
		System.out.println("\t-npf|-net_property_filename - Set properties filename to load the network properties from.");
	}

	/**
	 * Parse the arguments. We assume the status object instance (LociStatus) has previously been
	 * constructed (by calling initStatus()) before this method is invoked, so we can update the property
	 * filenames used by status.load() to load the instrument configurations.
	 * @param args The string list of arguments.
	 * @see #logLevel
	 * @see #status
	 * @see ngat.loci.LociStatus#setPropertyFilename
	 * @see ngat.loci.LociStatus#setNetworkPropertyFilename
	 * @see ngat.loci.LociStatus#setFitsPropertyFilename
	 * @see ngat.loci.LociStatus#setCurrentFilterPropertyFilename
	 * @see ngat.loci.LociStatus#setFilterPropertyFilename
	 */
	private void parseArguments(String args[])
	{
		for(int i = 0; i < args.length;i++)
		{
			if(args[i].equals("-cfpf")||args[i].equals("-current_filter_property_filename"))
			{
				if((i+1)< args.length)
				{
					status.setCurrentFilterPropertyFilename(args[i+1]);
					i++;
				}
				else
					System.err.println("-current_filter_property_filename requires a properties filename.");
			}
			else if(args[i].equals("-filterpf")||args[i].equals("-filter_property_filename"))
			{
				if((i+1)< args.length)
				{
					status.setFilterPropertyFilename(args[i+1]);
					i++;
				}
				else
					System.err.println("-filter_property_filename requires a properties filename.");
			}
			else if(args[i].equals("-fitspf")||args[i].equals("-fits_property_filename"))
			{
				if((i+1)< args.length)
				{
					status.setFitsPropertyFilename(args[i+1]);
					i++;
				}
				else
					System.err.println("-fits_property_filename requires a properties filename.");
			}
			else if(args[i].equals("-lpf")||args[i].equals("-loci_property_filename"))
			{
				if((i+1)< args.length)
				{
					status.setPropertyFilename(args[i+1]);
					i++;
				}
				else
					System.err.println("-loci_property_filename requires a properties filename.");
			}
			else if(args[i].equals("-l")||args[i].equals("-log"))
			{
				if((i+1)< args.length)
				{
					logLevel = Integer.parseInt(args[i+1]);
					i++;
				}
				else
					System.err.println("-log requires a log level");
			}
			else if(args[i].equals("-npf")||args[i].equals("-net_property_filename"))
			{
				if((i+1)< args.length)
				{
					status.setNetworkPropertyFilename(args[i+1]);
					i++;
				}
				else
					System.err.println("-net_property_filename requires a properties filename.");
			}
			else if(args[i].equals("-h")||args[i].equals("-help"))
			{
				help();
				System.exit(0);
			}
			else
				System.err.println("Loci '"+args[i]+"' not a recognised option");
		}// end for
	}

	/**
	 * The main routine, called when Loci is executed. This creates a new instance of the Loci class.
	 * It calls the following methods:
	 * <ul>
	 * <li>Calls the initStatus() method. This initialises the status (LociStatus) instance, so parseArguments
	 *     can modify the default proprty filenames before the properties are loaded in the init() method.
	 * <li>Calls the parseArguments().
	 * <li>Calls the init() method.
	 * <li>Calls the run() method.
	 * </ul>
	 * @param args The command line arguments.
	 * @see #init
	 * @see #parseArguments
	 * @see #run
	 */
	public static void main(String[] args)
	{
		Loci loci = new Loci();

		try
		{
			// create status object
			System.out.println(loci.getClass().getName()+":main:initStatus.");
			loci.initStatus();
			// parse arguments, update filenames to be used for config files in Loci's status object
			System.out.println(loci.getClass().getName()+":main:parseArguments.");
			loci.parseArguments(args);
			// initialise (load) Loci config files, and then initialise other things based on the
			// loaded configuration
			System.out.println(loci.getClass().getName()+":main:init.");
			loci.init();
		}
		catch(Exception e)
		{
 			loci.error("main:init failed:",e);
			System.exit(1);
		}
		// initialise the CCD Flask API (set temperature / cooler status)
		try
		{
			loci.initCCDController();
		}
		catch(Exception e)
		{
 			loci.error("main:initCCDController failed:",e);
			System.exit(1);
		}
		loci.run();
	// We get here if the server thread has terminated. If it has been quit
	// this is a successfull termination, otherwise an error has occured.
	// Note the program can also be terminated from within a REBOOT call.
		if(loci.server.getQuit() == false)
			System.exit(1);
	}
}

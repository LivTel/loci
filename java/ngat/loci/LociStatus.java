// LociStatus.java
// $Id$
package ngat.loci;

import java.lang.*;
import java.io.*;
import java.util.*;

import ngat.message.ISS_INST.*;
import ngat.phase2.*;
import ngat.util.PersistentUniqueInteger;
import ngat.util.FileUtilitiesNativeException;
import ngat.util.logging.FileLogHandler;

/**
 * This class holds status information for the Loci program.
 * @author Chris Mottram
 * @version $Revision$
 */
public class LociStatus
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * Default filename containing network properties for loci.
	 */
	private final static String DEFAULT_NET_PROPERTY_FILE_NAME = "./loci.net.properties";
	/**
	 * Default filename containing properties for loci.
	 */
	private final static String DEFAULT_PROPERTY_FILE_NAME = "./loci.properties";
	/**
	 * Default filename containing FITS properties for loci.
	 */
	private final static String DEFAULT_FITS_PROPERTY_FILE_NAME = "./fits.properties";
	/**
	 * Default filename containing filter wheel properties for loci.
	 */
	private final static String DEFAULT_CURRENT_FILTER_PROPERTY_FILE_NAME = "/icc/config/current.filter.properties";
	/**
	 * Default filename containing filter wheel properties for loci.
	 */
	private final static String DEFAULT_FILTER_PROPERTY_FILE_NAME = "/icc/config/filter.properties";
	/**
	 * The filename to use for loading network property configuration. Defaults to DEFAULT_NET_PROPERTY_FILE_NAME.
	 * Used by the load() method.
	 * @see #DEFAULT_NET_PROPERTY_FILE_NAME
	 * @see #load
	 */
	private String netPropertyFilename = DEFAULT_NET_PROPERTY_FILE_NAME;
	/**
	 * The filename to use for loading Loci property configuration. Defaults to DEFAULT_PROPERTY_FILE_NAME.
	 * Used by the load() method.
	 * @see #DEFAULT_PROPERTY_FILE_NAME
	 * @see #load
	 */
	private String lociPropertyFilename = DEFAULT_PROPERTY_FILE_NAME;
	/**
	 * The filename to use for loading FITS property configuration. Defaults to DEFAULT_FITS_PROPERTY_FILE_NAME.
	 * Used by the load() method.
	 * @see #DEFAULT_FITS_PROPERTY_FILE_NAME
	 * @see #load
	 */
	private String fitsPropertyFilename = DEFAULT_FITS_PROPERTY_FILE_NAME;
	/**
	 * The filename to use for loading the current filter property configuration. 
	 * Defaults to DEFAULT_CURRENT_FILTER_PROPERTY_FILE_NAME.
	 * Used by the load() method.
	 * @see #DEFAULT_CURRENT_FILTER_PROPERTY_FILE_NAME
	 * @see #load
	 */
	private String currentFilterPropertyFilename = DEFAULT_CURRENT_FILTER_PROPERTY_FILE_NAME;
	/**
	 * The filename to use for loading the filter property configuration. 
	 * Defaults to DEFAULT_FILTER_PROPERTY_FILE_NAME.
	 * Used by the load() method.
	 * @see #DEFAULT_CURRENT_FILTER_PROPERTY_FILE_NAME
	 * @see #load
	 */
	private String filterPropertyFilename = DEFAULT_FILTER_PROPERTY_FILE_NAME;
	/**
	 * The logging level. An absolute filter is used by the loggers. See:
	 * <ul>
	 * <li><a href="http://ltdevsrv.livjm.ac.uk/~dev/log_udp/cdocs/log_udp.html#LOG_VERBOSITY">LOG_VERBOSITY</a>
	 * <li><a href="http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_VERY_TERSE">VERBOSITY_VERY_TERSE</a>
	 * <li><a href="http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_TERSE">VERBOSITY_TERSE</a>
	 * <li><a href="http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_INTERMEDIATE">VERBOSITY_INTERMEDIATE</a>
	 * <li><a href="http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_VERBOSE">VERBOSITY_VERBOSE</a>
	 * <li><a href="http://ltdevsrv.livjm.ac.uk/~dev/ngat/javadocs/ngat/util/logging/ngat/util/logging/Logging.html#VERBOSITY_VERY_VERBOSE">VERBOSITY_VERY_VERBOSE</a>
	 * </ul>
	 */
	private int logLevel = 0;
	/**
	 * The current thread that the Loci Control System is using to process the
	 * <a href="#currentCommand">currentCommand</a>. This does not get set for
	 * commands that can be sent while others are in operation, such as Abort and get status comamnds.
	 * This can be null when no command is currently being processed.
	 */
	private Thread currentThread = null;
	/**
	 * The current command that the LociControl System is working on. This does not get set for
	 * commands that can be sent while others are in operation, such as Abort and get status comamnds.
	 * This can be null when no command is currently being processed.
	 */
	private ISS_TO_INST currentCommand = null;
	/**
	 * A list of properties held in the properties file. This contains configuration information in loci
	 * that needs to be changed irregularily.
	 */
	private Properties properties = null;
	/**
	 * The count of the number of exposures needed for the current command to be implemented.
	 */
	private int exposureCount = 0;
	/**
	 * The number of the current exposure being taken.
	 */
	private int exposureNumber = 0;
	/**
	 * The filename of the current exposure being taken (if any).
	 */
	private String exposureFilename = null;
	/**
	 * The current unique config ID, held on disc over reboots.
	 * Incremented each time a new configuration is attained,
	 * and stored in the FITS header.
	 */
	private PersistentUniqueInteger configId = null;
	/**
	 * The name of the ngat.phase2.LociConfig object instance that was last used	
	 * to configure the instrument (via an ngat.message.ISS_INST.CONFIG message).
	 * Used for the CONFNAME FITS keyword value.
	 * Initialised to 'UNKNOWN', so that if we try to take a frame before configuring Loci
	 * we get an error about setup not being complete, rather than an error about NULL FITS values.
	 */
	private String configName = "UNKNOWN";

	/**
	 * Default constructor. Initialises the properties.
	 * @see #properties
	 */
	public LociStatus()
	{
		properties = new Properties();
	}

	/**
	 * Set the network property filename, that load() uses to load Loci network configuration.
	 * This method should be called before the load() method is invoked.
	 * @param filename A string, the filename of the network properties file.
	 * @see #netPropertyFilename
	 * @see #load
	 */
	public void setNetworkPropertyFilename(String filename)
	{
		netPropertyFilename = filename;
	}
	
	/**
	 * Set the main Loci property filename, that load() uses to load Loci configuration.
	 * This method should be called before the load() method is invoked.
	 * @param filename A string, the filename of the main Loci properties file.
	 * @see #lociPropertyFilename
	 * @see #load
	 */
	public void setPropertyFilename(String filename)
	{
		lociPropertyFilename = filename;
	}
	
	/**
	 * Set the FITS property filename, that load() uses to load Loci FITS configuration.
	 * This method should be called before the load() method is invoked.
	 * @param filename A string, the filename of the FITS properties file.
	 * @see #fitsPropertyFilename
	 * @see #load
	 */
	public void setFitsPropertyFilename(String filename)
	{
		fitsPropertyFilename = filename;
	}
	
	/**
	 * Set the per-semester current filter property filename, that load() uses to load the 
	 * Loci currently installed filter configuration.
	 * This method should be called before the load() method is invoked.
	 * @param filename A string, the filename of the current filter properties file.
	 * @see #currentFilterPropertyFilename
	 * @see #load
	 */
	public void setCurrentFilterPropertyFilename(String filename)
	{
		currentFilterPropertyFilename = filename;
	}
	
	/**
	 * Set the filter database property filename, that load() uses to load the Loci filter database configuration.
	 * This method should be called before the load() method is invoked.
	 * @param filename A string, the filename of the filter database properties file.
	 * @see #filterPropertyFilename
	 * @see #load
	 */
	public void setFilterPropertyFilename(String filename)
	{
		filterPropertyFilename = filename;
	}
	
	/**
	 * The load method for the class. This loads the property file from disc, using the specified
	 * filename. Any old properties are first cleared.
	 * The configId unique persistent integer is then initialised, using a filename stored in the properties.
	 * @see #properties
	 * @see #initialiseConfigId
	 * @see #netPropertyFilename
	 * @see #lociPropertyFilename
	 * @see #fitsPropertyFilename
	 * @see #currentFilterPropertyFilename
	 * @see #filterPropertyFilename
	 * @exception FileNotFoundException Thrown if a configuration file is not found.
	 * @exception IOException Thrown if an IO error occurs whilst loading a configuration file.
	 */
	public void load()  throws FileNotFoundException, IOException
	{
		FileInputStream fileInputStream = null;

		System.out.println(this.getClass().getName()+":load:Started.");
	// clear old properties
		System.out.println(this.getClass().getName()+":load:Clearing properties.");
		properties.clear();
	// network properties load
		System.out.println(this.getClass().getName()+":load:Loading network properties from:"+
				   netPropertyFilename);
		fileInputStream = new FileInputStream(netPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// normal properties load
		System.out.println(this.getClass().getName()+":load:Loading Loci properties from:"+
				   lociPropertyFilename);
		fileInputStream = new FileInputStream(lociPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// fits properties load
		System.out.println(this.getClass().getName()+":load:Loading FITS properties from:"+
				   fitsPropertyFilename);
		fileInputStream = new FileInputStream(fitsPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// current filter properties laod
		System.out.println(this.getClass().getName()+":load:Loading current filter properties from:"+
				   currentFilterPropertyFilename);
		fileInputStream = new FileInputStream(currentFilterPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// filter properties laod
		System.out.println(this.getClass().getName()+":load:Loading filter properties from:"+
				   filterPropertyFilename);
		fileInputStream = new FileInputStream(filterPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// initialise configId
		System.out.println(this.getClass().getName()+":load:Initialising config id.");
		initialiseConfigId();
	}

	/**
	 * The reload method for the class. This reloads the specified property files from disc.
	 * The current properties are not cleared, as network properties are not re-loaded, as this would
	 * involve resetting up the server connection thread which may be in use. If properties have been
	 * deleted from the loaded files, reload does not clear these properties. Any new properties or
	 * ones where the values have changed will change.
	 * The configId unique persistent integer is then initialised, using a filename stored in the properties.
	 * @see #properties
	 * @see #initialiseConfigId
	 * @see #lociPropertyFilename
	 * @see #fitsPropertyFilename
	 * @see #currentFilterPropertyFilename
	 * @see #filterPropertyFilename
	 * @exception FileNotFoundException Thrown if a configuration file is not found.
	 * @exception IOException Thrown if an IO error occurs whilst loading a configuration file.
	 */
	public void reload() throws FileNotFoundException,IOException
	{
		FileInputStream fileInputStream = null;

	// don't clear old properties, the network properties are not re-loaded
	// normal properties load
		fileInputStream = new FileInputStream(lociPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// fits properties load
		fileInputStream = new FileInputStream(fitsPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// current filter properties laod
		fileInputStream = new FileInputStream(currentFilterPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// filter properties laod
		fileInputStream = new FileInputStream(filterPropertyFilename);
		properties.load(fileInputStream);
		fileInputStream.close();
	// initialise configId
		initialiseConfigId();
	}

	/**
	 * Set the logging level for Loci.
	 * @param level The level of logging.
	 */
	public synchronized void setLogLevel(int level)
	{
		logLevel = level;
	}

	/**
	 * Get the logging level for Loci.
	 * @return The current log level.
	 */	
	public synchronized int getLogLevel()
	{
		return logLevel;
	}

	/**
	 * Set the command that is currently executing.
	 * @param command The command that is currently executing.
	 */
	public synchronized void setCurrentCommand(ISS_TO_INST command)
	{
		currentCommand = command;
	}

	/**
	 * Get the the command Loci is currently processing.
	 * @return The command currently being processed.
	 */
	public synchronized ISS_TO_INST getCurrentCommand()
	{
		return currentCommand;
	}

	/**
	 * Set the thread that is currently executing the <a href="#currentCommand">currentCommand</a>.
	 * @param thread The thread that is currently executing.
	 * @see #currentThread
	 */
	public synchronized void setCurrentThread(Thread thread)
	{
		currentThread = thread;
	}

	/**
	 * Get the the thread currently executing to process the <a href="#currentCommand">currentCommand</a>.
	 * @return The thread currently being executed.
	 * @see #currentThread
	 */
	public synchronized Thread getCurrentThread()
	{
		return currentThread;
	}

	/**
	 * Set the number of exposures needed to complete the current command implementation.
	 * @param c The total number of exposures needed.
	 * @see #exposureCount
	 */
	public synchronized void setExposureCount(int c)
	{
		exposureCount = c;
	}

	/**
	 * Get the number of exposures needed to complete the current command implementation.
	 * @return Returns the number of exposures needed.
	 * @see #exposureCount
	 */
	public synchronized int getExposureCount()
	{
		return exposureCount;
	}

	/**
	 * Set the current exposure number the current command implementation is on.
	 * @param n The current exposure number.
	 * @see #exposureNumber
	 */
	public synchronized void setExposureNumber(int n)
	{
		exposureNumber = n;
	}

	/**
	 * Get the current exposure number the current command implementation is on.
	 * @return Returns the current exposure number.
	 * @see #exposureNumber
	 */
	public synchronized int getExposureNumber()
	{
		return exposureNumber;
	}

	/**
	 * Set the current exposure filename being taken.
	 * @param f The current filename.
	 * @see #exposureFilename
	 */
	public synchronized void setExposureFilename(String f)
	{
		exposureFilename = f;
	}

	/**
	 * Get the current exposure filename.
	 * @return Returns the current exposure filename.
	 * @see #exposureFilename
	 */
	public synchronized String getExposureFilename()
	{
		return exposureFilename;
	}

	/**
	 * Method to change (increment) the unique ID number of the last ngat.phase2.LociConfig instance to 
	 * successfully configure the Loci camera.
	 * This is done by calling <i>configId.increment()</i>.
	 * @see #configId
	 * @see ngat.util.PersistentUniqueInteger#increment
	 * @exception FileUtilitiesNativeException Thrown if <i>PersistentUniqueInteger.increment()</i> fails.
	 * @exception NumberFormatException Thrown if <i>PersistentUniqueInteger.increment()</i> fails.
	 * @exception Exception Thrown if <i>PersistentUniqueInteger.increment()</i> fails.
	 */
	public synchronized void incConfigId() throws FileUtilitiesNativeException,
		NumberFormatException, Exception
	{
		configId.increment();
	}

	/**
	 * Method to get the unique config ID number of the last
	 * ngat.phase2.LociConfig instance to successfully configure the Loci camera.
	 * @return The unique config ID number.
	 * This is done by calling <i>configId.get()</i>.
	 * @see #configId
	 * @see ngat.util.PersistentUniqueInteger#get
	 * @exception FileUtilitiesNativeException Thrown if <i>PersistentUniqueInteger.get()</i> fails.
	 * @exception NumberFormatException Thrown if <i>PersistentUniqueInteger.get()</i> fails.
	 * @exception Exception Thrown if <i>PersistentUniqueInteger.get()</i> fails.
	 */
	public synchronized int getConfigId() throws FileUtilitiesNativeException,
		NumberFormatException, Exception
	{
		return configId.get();
	}

	/**
	 * Method to set our reference to the string identifier of the last
	 * ngat.phase2.LociConfig instance to successfully configure the Loci camera.
	 * @param s The string from the configuration object instance.
	 * @see #configName
	 */
	public synchronized void setConfigName(String s)
	{
		configName = s;
	}

	/**
	 * Method to get the string identifier of the last
	 * ngat.phase2.LociConfig instance to successfully configure the Loci camera.
	 * @return The string identifier, or null if the Loci camera has not been configured
	 * 	since Loci started.
	 * @see #configName
	 */
	public synchronized String getConfigName()
	{
		return configName;
	}

	/**
	 * Method to return whether the loaded properties contain the specified keyword.
	 * Calls the proprties object containsKey method. Note assumes the properties object has been initialised.
	 * @param p The property key we wish to test exists.
	 * @return The method returnd true if the specified key is a key in out list of properties,
	 *         otherwise it returns false.
	 * @see #properties
	 */
	public boolean propertyContainsKey(String p)
	{
		return properties.containsKey(p);
	}

	/**
	 * Routine to get a properties value, given a key. Just calls the properties object getProperty routine.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a string object. If the key is not found, Properties.getProperty
	 *         will return null.
	 * @see #properties
	 */
	public String getProperty(String p)
	{
		return properties.getProperty(p);
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid integer, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as an integer.
	 * @exception NumberFormatException If the properties value string is not a valid integer, this
	 * 	exception will be thrown when the Integer.parseInt routine is called.
	 * @see #properties
	 */
	public int getPropertyInteger(String p) throws NumberFormatException
	{
		String valueString = null;
		int returnValue = 0;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Integer.parseInt(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyInteger:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue;
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid long, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a long.
	 * @exception NumberFormatException If the properties value string is not a valid long, this
	 * 	exception will be thrown when the Long.parseLong routine is called.
	 * @see #properties
	 */
	public long getPropertyLong(String p) throws NumberFormatException
	{
		String valueString = null;
		long returnValue = 0;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Long.parseLong(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyLong:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue;
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid short, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a short.
	 * @exception NumberFormatException If the properties value string is not a valid short, this
	 * 	exception will be thrown when the Short.parseShort routine is called.
	 * @see #properties
	 */
	public short getPropertyShort(String p) throws NumberFormatException
	{
		String valueString = null;
		short returnValue = 0;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Short.parseShort(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyShort:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue;
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid double, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as an double.
	 * @exception NumberFormatException If the properties value string is not a valid double, this
	 * 	exception will be thrown when the Double.valueOf routine is called.
	 * @see #properties
	 */
	public double getPropertyDouble(String p) throws NumberFormatException
	{
		String valueString = null;
		Double returnValue = null;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Double.valueOf(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyDouble:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue.doubleValue();
	}

	/**
	 * Routine to get a properties value, given a key. The value must be a valid float, else a 
	 * NumberFormatException is thrown.
	 * @param p The property key we want the value for.
	 * @return The properties value, as a float.
	 * @exception NumberFormatException If the properties value string is not a valid float, this
	 * 	exception will be thrown.
	 * @see #properties
	 */
	public float getPropertyFloat(String p) throws NumberFormatException
	{
		String valueString = null;
		Float returnValue = null;

		valueString = properties.getProperty(p);
		try
		{
			returnValue = Float.valueOf(valueString);
		}
		catch(NumberFormatException e)
		{
			// re-throw exception with more information e.g. keyword
			throw new NumberFormatException(this.getClass().getName()+":getPropertyFloat:keyword:"+
				p+":valueString:"+valueString);
		}
		return returnValue.floatValue();
	}

	/**
	 * Routine to get a properties boolean value, given a key. The properties value should be either 
	 * "true" or "false".
	 * Boolean.valueOf is used to convert the string to a boolean value.
	 * @param p The property key we want the boolean value for.
	 * @return The properties value, as an boolean.
	 * @exception NullPointerException If the properties value string is null, this
	 * 	exception will be thrown.
	 * @see #properties
	 */
	public boolean getPropertyBoolean(String p) throws NullPointerException
	{
		String valueString = null;
		Boolean b = null;

		valueString = properties.getProperty(p);
		if(valueString == null)
		{
			throw new NullPointerException(this.getClass().getName()+":getPropertyBoolean:keyword:"+
				p+":Value was null.");
		}
		b = Boolean.valueOf(valueString);
		return b.booleanValue();
	}

	/**
	 * Routine to get a properties character value, given a key. The properties value should be a 1 letter string.
	 * @param p The property key we want the character value for.
	 * @return The properties value, as a character.
	 * @exception NullPointerException If the properties value string is null, this
	 * 	exception will be thrown.
	 * @exception Exception Thrown if the properties value string is not of length 1.
	 * @see #properties
	 */
	public char getPropertyChar(String p) throws NullPointerException, Exception
	{
		String valueString = null;
		char ch;

		valueString = properties.getProperty(p);
		if(valueString == null)
		{
			throw new NullPointerException(this.getClass().getName()+":getPropertyChar:keyword:"+
				p+":Value was null.");
		}
		if(valueString.length() != 1)
		{
			throw new Exception(this.getClass().getName()+":getPropertyChar:keyword:"+
					    p+":Value not of length 1, had length "+valueString.length());
		}
		ch = valueString.charAt(0);
		return ch;
	}

	/**
	 * Routine to get an integer representing a ngat.util.logging.FileLogHandler time period.
	 * The value of the specified property should contain either:'HOURLY_ROTATION', 'DAILY_ROTATION' or
	 * 'WEEKLY_ROTATION'.
	 * @param p The property key we want the time period value for.
	 * @return The properties value, as an FileLogHandler time period (actually an integer).
	 * @exception NullPointerException If the properties value string is null an exception is thrown.
	 * @exception IllegalArgumentException If the properties value string is not a valid time period,
	 *            an exception is thrown.
	 * @see #properties
	 */
	public int getPropertyLogHandlerTimePeriod(String p) throws NullPointerException, IllegalArgumentException
	{
		String valueString = null;
		int timePeriod = 0;
 
		valueString = properties.getProperty(p);
		if(valueString == null)
		{
			throw new NullPointerException(this.getClass().getName()+
						       ":getPropertyLogHandlerTimePeriod:keyword:"+
						       p+":Value was null.");
		}
		if(valueString.equals("HOURLY_ROTATION"))
			timePeriod = FileLogHandler.HOURLY_ROTATION;
		else if(valueString.equals("DAILY_ROTATION"))
			timePeriod = FileLogHandler.DAILY_ROTATION;
		else if(valueString.equals("WEEKLY_ROTATION"))
			timePeriod = FileLogHandler.WEEKLY_ROTATION;
		else
		{
			throw new IllegalArgumentException(this.getClass().getName()+
							   ":getPropertyLogHandlerTimePeriod:keyword:"+
							   p+":Illegal value:"+valueString+".");
		}
		return timePeriod;
	}
	
	/**
	 * Method to get a filter's Id name from it's type name. This information is stored in the
	 * per-semester filter property file, under the 'filterwheel.&lt;filterTypeName&gt;.id' property.
	 * @param filterTypeName The filter type name to get the actual filter id from.
	 * @return A string, which is the unique filter id for this type of filter.
	 * @exception IllegalArgumentException Thrown if the specified property cannot be found.
	 */
	public String getFilterIdName(String filterTypeName)
	{
		String s = null;

	// get the filter id name into s
		s = getProperty("filterwheel."+filterTypeName+".id");
		if(s == null)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
				":getFilterIdName:Filter id name not found in property:filterwheel."+
				filterTypeName+".id");
		}
		return s;
	}

	/**
	 * Method to get a filter's Id optical thickness from it's name . This information is stored in the
	 * filter database property file, under the 'filter.&lt;filterIdName&gt;.optical_thickness' property.
	 * The filter's Id string can be retrieved from a filter type string using getFilterIdName.
	 * @param filterIdName The filter id name to get the optical thickness for.
	 * @return A double, which is the optical thickness of the given filter.
	 * @exception IllegalArgumentException Thrown if the specified property/filter id cannot be found.
	 * @exception NumberFormatException Thrown if the property cannot be parsed.
	 * @see #getFilterIdName
	 */
	public double getFilterIdOpticalThickness(String filterIdName) throws NumberFormatException
	{
		String s = null;

	// get the filter id name into s
		s = getProperty("filter."+filterIdName+".optical_thickness");
		if(s == null)
		{
			throw new IllegalArgumentException(this.getClass().getName()+
				":getFilterIdOpticalThickness:Property not found/is null:filter."+
				filterIdName+".optical_thickness");
		}
		return Double.parseDouble(s);
	}

	/**
	 * Method to get the thread priority to run the server thread at.
	 * The value is retrieved from the <b>loci.thread.priority.server</b> property.
	 * If this fails the default LOCI_DEFAULT_THREAD_PRIORITY_SERVER is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see LociConstants#LOCI_DEFAULT_THREAD_PRIORITY_SERVER
	 */
	public int getThreadPriorityServer()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("loci.thread.priority.server");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = LociConstants.LOCI_DEFAULT_THREAD_PRIORITY_SERVER;
		}
		return retval;
	}

	/**
	 * Method to get the thread priority to run interrupt threads at.
	 * The value is retrieved from the <b>loci.thread.priority.interrupt</b> property.
	 * If this fails the default LOCI_DEFAULT_THREAD_PRIORITY_INTERRUPT is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see LociConstants#LOCI_DEFAULT_THREAD_PRIORITY_INTERRUPT
	 */
	public int getThreadPriorityInterrupt()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("loci.thread.priority.interrupt");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = LociConstants.LOCI_DEFAULT_THREAD_PRIORITY_INTERRUPT;
		}
		return retval;
	}

	/**
	 * Method to get the thread priority to run normal threads at.
	 * The value is retrieved from the <b>loci.thread.priority.normal</b> property.
	 * If this fails the default LOCI_DEFAULT_THREAD_PRIORITY_NORMAL is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see LociConstants#LOCI_DEFAULT_THREAD_PRIORITY_NORMAL
	 */
	public int getThreadPriorityNormal()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("loci.thread.priority.normal");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = LociConstants.LOCI_DEFAULT_THREAD_PRIORITY_NORMAL;
		}
		return retval;
	}

	/**
	 * Method to get the thread priority to run the Telescope Image Transfer server and client 
	 * connection threads at.
	 * The value is retrieved from the <b>loci.thread.priority.tit</b> property.
	 * If this fails the default LOCI_DEFAULT_THREAD_PRIORITY_TIT is returned.
	 * @return A valid thread priority between threads MIN_PRIORITY and MAX_PRIORITY.
	 * @see LociConstants#LOCI_DEFAULT_THREAD_PRIORITY_TIT
	 */
	public int getThreadPriorityTIT()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("loci.thread.priority.tit");
			if(retval < Thread.MIN_PRIORITY)
				retval = Thread.MIN_PRIORITY;
			if(retval > Thread.MAX_PRIORITY)
				retval = Thread.MAX_PRIORITY;
		}
		catch(NumberFormatException e)
		{
			retval = LociConstants.LOCI_DEFAULT_THREAD_PRIORITY_TIT;
		}
		return retval;
	}

	/**
	 * Method to get the maximum length of time a readout takes, in millseconds.
	 * The value is retrieved from the <b>loci.config.readout_time.max</b> property.
	 * If this fails zero is returned.
	 * The value is used when calculating ACK times.
	 * @return The maximum length of time a readout takes, in millseconds.
	 * @see #getPropertyInteger
	 */
	public int getMaxReadoutTime()
	{
		int retval;

		try
		{
			retval = getPropertyInteger("loci.config.readout_time.max");
		}
		catch(NumberFormatException e)
		{
			retval = 0;
		}
		return retval;
	}

	/**
	 * Internal method to initialise the configId field. This is not done during construction
	 * as the property files need to be loaded to determine the filename to use.
	 * This is got from the <i>loci.config.unique_id_filename</i> property.
	 * The configId field is then constructed.
	 * @see #configId
	 */
	private void initialiseConfigId()
	{
		String fileName = null;

		fileName = getProperty("loci.config.unique_id_filename");
		configId = new PersistentUniqueInteger(fileName);
	}
}

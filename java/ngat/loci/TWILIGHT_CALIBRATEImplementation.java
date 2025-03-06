// TWILIGHT_CALIBRATEImplementation.java
// $Id$
package ngat.loci;

import java.io.*;
import java.lang.*;
import java.util.*;


import ngat.fits.*;
import ngat.loci.ccd.*;
import ngat.message.base.*;

import ngat.message.ISS_INST.OFFSET_FOCUS;
import ngat.message.ISS_INST.OFFSET_FOCUS_DONE;
import ngat.message.ISS_INST.OFFSET_RA_DEC;
import ngat.message.ISS_INST.OFFSET_RA_DEC_DONE;
import ngat.message.ISS_INST.INST_TO_ISS_DONE;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE_ACK;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE_DP_ACK;
import ngat.message.ISS_INST.TWILIGHT_CALIBRATE_DONE;
import ngat.util.*;
import ngat.util.logging.*;


/**
 * This class provides the implementation of a TWILIGHT_CALIBRATE command sent to a server using the
 * Java Message System. It performs a series of SKYFLAT frames from a configurable list,
 * taking into account frames done in previous invocations of this command (it saves it's state).
 * The exposure length is dynamically adjusted as the sky gets darker or brighter. TWILIGHT_CALIBRATE commands
 * should be sent to Loci just after sunset and just before sunrise.
 * @author Chris Mottram
 * @version $Revision: 1.5 $
 */
public class TWILIGHT_CALIBRATEImplementation2 extends CALIBRATEImplementation implements JMSCommandImplementation
{
	/**
	 * Revision Control System id string, showing the version of the Class.
	 */
	public final static String RCSID = new String("$Id$");
	/**
	 * The number of different binning factors we should min/best/max count data for.
	 * Actually 1 more than the maximum used binning, as we go from 1 not 0.
	 * @see #minMeanCounts
	 * @see #maxMeanCounts
	 * @see #bestMeanCounts
	 */
	protected final static int BIN_COUNT 	     = 5;
	/**
	 * Initial part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_STRING = "loci.twilight_calibrate.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_CALIBRATION_STRING = "calibration.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_SUNSET_STRING = "sunset.";
	/**
	 * Middle part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_SUNRISE_STRING = "sunrise.";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_BIN_STRING = ".bin";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_FILTER_STRING = ".filter";
	/**
	 * Final part of a key string, used to create a list of potential twilight calibrations to
	 * perform from a Java property file.
	 */
	protected final static String LIST_KEY_FREQUENCY_STRING = ".frequency";
	/**
	 * Middle part of a key string, used for saving and restoring the stored calibration state.
	 */
	protected final static String LIST_KEY_LAST_TIME_STRING = "last_time.";
	/**
	 * Middle part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_OFFSET_STRING = "offset.";
	/**
	 * Last part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_RA_STRING = ".ra";
	/**
	 * Last part of a key string, used to load a list of telescope RA/DEC offsets from a Java property file.
	 */
	protected final static String LIST_KEY_DEC_STRING = ".dec";
	/**
	 * Middle part of a key string, used to get comparative filter sensitivity.
	 */
	protected final static String LIST_KEY_FILTER_SENSITIVITY_STRING = "filter_sensitivity.";
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_UNKNOWN = 0;
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_SUNSET	= 1;
	/**
	 * Constant used for time of night determination.
	 * @see #timeOfNight
	 */
	protected final static int TIME_OF_NIGHT_SUNRISE = 2;
	/**
	 * A possible state of a frame taken by this command. 
	 * The frame did not have enough counts to be useful, i.e. the mean counts were less than minMeanCounts[bin].
	 * @see #minMeanCounts
	 */
	protected final static int FRAME_STATE_UNDEREXPOSED 	= 0;
	/**
	 * A possible state of a frame taken by this command. 
	 * The mean counts for the frame were sensible, i.e. the mean counts were more than minMeanCounts[bin] and less
	 * than maxMeanCounts[bin].
	 * @see #minMeanCounts
	 * @see #maxMeanCounts
	 */
	protected final static int FRAME_STATE_OK 		= 1;
	/**
	 * A possible state of a frame taken by this command. 
	 * The frame had too many counts to be useful, i.e. the mean counts were higher than maxMeanCounts[bin].
	 * @see #maxMeanCounts
	 */
	protected final static int FRAME_STATE_OVEREXPOSED 	= 2;
	/**
	 * The number of possible frame states.
	 * @see #FRAME_STATE_UNDEREXPOSED
	 * @see #FRAME_STATE_OK
	 * @see #FRAME_STATE_OVEREXPOSED
	 */
	protected final static int FRAME_STATE_COUNT 	= 3;
	/**
	 * Description strings for the frame states, indexed by the frame state enumeration numbers.
	 * @see #FRAME_STATE_UNDEREXPOSED
	 * @see #FRAME_STATE_OK
	 * @see #FRAME_STATE_OVEREXPOSED
	 * @see #FRAME_STATE_COUNT
	 */
	protected final static String FRAME_STATE_NAME_LIST[] = {"underexposed","ok","overexposed"};
	/**
	 * The time, in milliseconds since the epoch, that the implementation of this command was started.
	 */
	private long implementationStartTime = 0L;
	/**
	 * The saved state of calibrations done over time by invocations of this command.
	 * @see TWILIGHT_CALIBRATESavedState
	 */
	private TWILIGHT_CALIBRATESavedState twilightCalibrateState = null;
	/**
	 * The filename holding the saved state data.
	 */
	private String stateFilename = null;
	/**
	 * The list of calibrations to select from.
	 * Each item in the list is an instance of TWILIGHT_CALIBRATECalibration.
	 * @see TWILIGHT_CALIBRATECalibration
	 */
	protected List calibrationList = null;
	/**
	 * The list of telescope offsets to do each calibration for.
	 * Each item in the list is an instance of TWILIGHT_CALIBRATEOffset.
	 * @see TWILIGHT_CALIBRATEOffset
	 */
	protected List offsetList = null;
	/**
	 * The frame overhead for a full frame, in milliseconds. This takes into account readout time,
	 * real time data reduction, communication overheads and the like.
	 */
	private int frameOverhead = 0;
	/**
	 * The minimum allowable exposure time for a frame, in milliseconds.
	 */
	private int minExposureLength = 0;
	/**
	 * The maximum allowable exposure time for a frame, in milliseconds.
	 */
	private int maxExposureLength = 0;
	/**
	 * The exposure time for the current frame, in milliseconds.
	 */
	private int exposureLength = 0;
	/**
	 * The exposure time for the last frame exposed, in milliseconds.
	 */
	private int lastExposureLength = 0;
	/**
	 * What time of night we are doing the calibration, is it sunset or sunrise.
	 * @see #TIME_OF_NIGHT_UNKNOWN
	 * @see #TIME_OF_NIGHT_SUNSET
	 * @see #TIME_OF_NIGHT_SUNRISE
	 */
	private int timeOfNight = TIME_OF_NIGHT_UNKNOWN;
	/**
	 * Filename used to save FITS frames to, until they are determined to contain valid data
	 * (the counts in them are within limits).
	 */
	private String temporaryFITSFilename = null;
	/**
	 * The last mean counts measured by the DpRt on the last frame exposed.
	 * Probably associated with the exposure length lastExposureLength most of the time.
	 */
	private float meanCounts;
	/**
	 * The minimum mean counts. A &quot;good&quot; frame will have a mean counts greater than this number.
	 * A list, indexed by binning factor, as the chip has different saturation values at different binnings.
	 * The array must be BIN_COUNT size long.
	 */
	private int minMeanCounts[] = {0,0,0,0,0};
	/**
	 * The best mean counts. The &quot;ideal&quot; frame will have a mean counts of this number.
	 * A list, indexed by binning factor, as the chip has different saturation values at different binnings.
	 * The array must be BIN_COUNT size long.
	 */
	private int bestMeanCounts[] = {0,0,0,0,0};
	/**
	 * The maximum mean counts. A &quot;good&quot; frame will have a mean counts less than this number.
	 * A list, indexed by binning factor, as the chip has different saturation values at different binnings.
	 * The array must be BIN_COUNT size long.
	 */
	private int maxMeanCounts[] = {0,0,0,0,0};
	/**
	 * The last relative filter sensitivity used for calculating exposure lengths.
	 */
	private double lastFilterSensitivity = 1.0;
	/**
	 * The last bin factor used for calculating exposure lengths.
	 */
	private int lastBin = 1;
	/**
	 * Loop terminator for the calibration list loop.
	 */
	private boolean doneCalibration = false;
	/**
	 * Loop terminator for the offset list loop.
	 */
	private boolean doneOffset = false;
	/**
	 * The number of calibrations completed that have a frame state of good,
	 * for the currently executing calibration.
	 * A calibration is successfully completed if the calibrationFrameCount is
	 * equal to the offsetList size at the end of the offset list loop.
	 */
	private int calibrationFrameCount = 0;

	/**
	 * Constructor.
	 */
	public TWILIGHT_CALIBRATEImplementation2()
	{
		super();
	}

	/**
	 * This method allows us to determine which class of command this implementation class implements.
	 * This method returns &quot;ngat.message.ISS_INST.TWILIGHT_CALIBRATE&quot;.
	 * @return A string, the classname of the class of ngat.message command this class implements.
	 */
	public static String getImplementString()
	{
		return "ngat.message.ISS_INST.TWILIGHT_CALIBRATE";
	}

	/**
	 * This method is the first to be called in this class. 
	 * <ul>
	 * <li>It calls the superclass's init method.
	 * </ul>
	 * @param command The command to be implemented.
	 */
	public void init(COMMAND command)
	{
		super.init(command);
	}

	/**
	 * This method gets the TWILIGHT_CALIBRATE command's acknowledge time. 
	 * This returns the server connection threads default acknowledge time.
	 * @param command The command instance we are implementing.
	 * @return An instance of ACK with the timeToComplete set.
	 * @see ngat.message.base.ACK#setTimeToComplete
	 * @see OTCPServerConnectionThread#getDefaultAcknowledgeTime
	 */
	public ACK calculateAcknowledgeTime(COMMAND command)
	{
		ACK acknowledge = null;

		acknowledge = new ACK(command.getId());
		acknowledge.setTimeToComplete(serverConnectionThread.getDefaultAcknowledgeTime());
		return acknowledge;
	}

	/**
	 * This method implements the TWILIGHT_CALIBRATE command.
	 * <ul>
	 * <li>The implementation start time is saved.
	 * <li><b>setTimeOfNight</b> is called to set the time of night flag.
	 * <li><b>loadProperties</b> is called to get configuration data from the properties.
	 * <li><b>addSavedStateToCalibration</b> is called, which finds the correct last time for each
	 * 	calibration in the list and sets the relevant field.
	 * <li>The FITS headers are cleared, and a the MULTRUN number is incremented.
	 * <li>The fold mirror is moved to the correct location using <b>moveFold</b>.
	 * <li>For each calibration, we do the following:
	 *      <ul>
	 *      <li><b>doCalibration</b> is called.
	 *      </ul>
	 * <li>sendBasicAck is called, to stop the client timing out whilst creating the master flat.
	 * <li>The makeMasterFlat method is called, to create master flat fields from the data just taken.
	 * </ul>
	 * Note this method assumes the loading and initialisation before the main loop takes less than the
	 * default acknowledge time, as no ACK's are sent to the client until we are ready to do the first
	 * sequence of calibration frames.
	 * @param command The command to be implemented.
	 * @return An instance of TWILIGHT_CALIBRATE_DONE is returned, with it's fields indicating
	 * 	the result of the command implementation.
	 * @see #implementationStartTime
	 * @see #exposureLength
	 * @see #lastFilterSensitivity
	 * @see #setTimeOfNight
	 * @see #addSavedStateToCalibration
	 * @see FITSImplementation#moveFold
	 * @see FITSImplementation#clearFitsHeaders
	 * @see #doCalibration
	 * @see #frameOverhead
	 * @see CALIBRATEImplementation#makeMasterFlat
	 */
	public COMMAND_DONE processCommand(COMMAND command)
	{
		TWILIGHT_CALIBRATE twilightCalibrateCommand = (TWILIGHT_CALIBRATE)command;
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone = new TWILIGHT_CALIBRATE_DONE(command.getId());
		TWILIGHT_CALIBRATECalibration calibration = null;
		int calibrationListIndex = 0;
		String directoryString = null;
		int makeFlatAckTime;

		twilightCalibrateDone.setMeanCounts(0.0f);
		twilightCalibrateDone.setPeakCounts(0.0f);
	// initialise
		implementationStartTime = System.currentTimeMillis();
		setTimeOfNight();
		if(loadProperties(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
	// initialise status/fits header info, in case any frames are produced.
		status.setExposureCount(-1);
		status.setExposureNumber(0);
	// match saved state to calibration list (put last time into calibration list)
		if(addSavedStateToCalibration(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
	// move the fold mirror to the correct location
		if(moveFold(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return twilightCalibrateDone;
		if(testAbort(twilightCalibrateCommand,twilightCalibrateDone) == true)
			return twilightCalibrateDone;
	// initialise exposureLength
		if(timeOfNight == TIME_OF_NIGHT_SUNRISE)
			exposureLength = maxExposureLength;
		else if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			exposureLength = minExposureLength;
		else // this should never happen
			exposureLength = minExposureLength;
		// set lastFilterSensitivity/lastBin to contents of first calibration so initial exposure length
		// remains the same as the calculated above.
		lastExposureLength = exposureLength;
		lastFilterSensitivity = 1.0;
		lastBin = 1;
		if(calibrationList.size() > 0)
		{
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(0));
			lastFilterSensitivity = calibration.getFilterSensitivity();
			lastBin = calibration.getBin();
		}
		// initialise meanCounts
		meanCounts = bestMeanCounts[lastBin];
		// initialise loop variables
		calibrationListIndex = 0;
		doneCalibration = false;
	// main loop, do calibrations until we run out of time.
		while((doneCalibration == false) && (calibrationListIndex < calibrationList.size()))
		{
		// get calibration
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(calibrationListIndex));
		// do calibration
			if(doCalibration(twilightCalibrateCommand,twilightCalibrateDone,calibration) == false)
				return twilightCalibrateDone;
			calibrationListIndex++;
		}// end for on calibration list
	// send an ack before make master processing, so the client doesn't time out.
		makeFlatAckTime = status.getPropertyInteger("loci.twilight_calibrate.acknowledge_time.make_flat");
		if(sendBasicAck(twilightCalibrateCommand,twilightCalibrateDone,makeFlatAckTime) == false)
			return twilightCalibrateDone;
	// Call pipeline to create master flat.
		diddly this config does not exists;
		directoryString = status.getProperty("loci.file.fits.path");
		if(directoryString.endsWith(System.getProperty("file.separator")) == false)
			directoryString = directoryString.concat(System.getProperty("file.separator"));
		if(makeMasterFlat(twilightCalibrateCommand,twilightCalibrateDone,directoryString) == false)
			return twilightCalibrateDone;
	// return done
		twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_NO_ERROR);
		twilightCalibrateDone.setErrorString("");
		twilightCalibrateDone.setSuccessful(true);
		return twilightCalibrateDone;
	}

	/**
	 * Method to set time of night flag.
	 * @see #TIME_OF_NIGHT_UNKNOWN
	 * @see #TIME_OF_NIGHT_SUNRISE
	 * @see #TIME_OF_NIGHT_SUNSET
	 * @see #timeOfNight
	 */
	protected void setTimeOfNight()
	{
		Calendar calendar = null;
		int hour;

		timeOfNight = TIME_OF_NIGHT_UNKNOWN;
	// get Instance initialises the calendar to the current time.
		calendar = Calendar.getInstance();
	// the hour returned using HOUR of DAY is between 0 and 23
		hour = calendar.get(Calendar.HOUR_OF_DAY);
		if(hour < 12)
			timeOfNight = TIME_OF_NIGHT_SUNRISE;
		else
			timeOfNight = TIME_OF_NIGHT_SUNSET;
	}

	/**
	 * Method to load twilight calibration configuration data from the LOCI Properties file.
	 * The following configuration properties are retrieved:
	 * <ul>
	 * <li>frame overhead
	 * <li>minimum exposure length
	 * <li>maximum exposure length
	 * <li>temporary FITS filename
	 * <li>saved state filename
	 * <li>minimum mean counts (for each binning factor)
	 * <li>best mean counts (for each binning factor)
	 * <li>maximum mean counts (for each binning factor)
	 * </ul>
	 * The following methods are then called to load more calibration data:
	 * <ul>
	 * <li><b>loadCalibrationList</b>
	 * <li><b>loadOffsetList</b>
	 * <li><b>loadState</b>
	 * </ul>
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if the method succeeds, false if an error occurs.
	 * 	If false is returned the error data in twilightCalibrateDone is filled in.
	 * @see #loadCalibrationList
	 * @see #loadState
	 * @see #loadOffsetList
	 * @see #frameOverhead
	 * @see #minExposureLength
	 * @see #maxExposureLength
	 * @see #temporaryFITSFilename
	 * @see #stateFilename
	 * @see #minMeanCounts
	 * @see #bestMeanCounts
	 * @see #maxMeanCounts
	 * @see #timeOfNight
	 * @see #LIST_KEY_SUNSET_STRING
	 * @see #LIST_KEY_SUNRISE_STRING
	 * @see #LIST_KEY_STRING
	 */
	protected boolean loadProperties(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		String timeOfNightString = null;
		String propertyName = null;

		if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			timeOfNightString = LIST_KEY_SUNSET_STRING;
		else
			timeOfNightString = LIST_KEY_SUNRISE_STRING;
		try
		{
		// frame overhead
			propertyName = LIST_KEY_STRING+"frame_overhead";
			frameOverhead = status.getPropertyInteger(propertyName);
		// minimum exposure length
			propertyName = LIST_KEY_STRING+"min_exposure_time";
			minExposureLength = status.getPropertyInteger(propertyName);
		// maximum exposure length
			propertyName = LIST_KEY_STRING+"max_exposure_time";
			maxExposureLength = status.getPropertyInteger(propertyName);
		// temporary FITS filename
		// This configuration cannot be used at the moment due to limitations in the loci camera API / filename API
			propertyName = LIST_KEY_STRING+"file.tmp";
			temporaryFITSFilename = status.getProperty(propertyName);
		// saved state filename
			propertyName = LIST_KEY_STRING+"state_filename";
			stateFilename = status.getProperty(propertyName);
			// binning goes from 1..BIN_COUNT-1
			for(int binIndex = 1; binIndex < BIN_COUNT; binIndex ++)
			{
				// minimum mean counts
				propertyName = LIST_KEY_STRING+"mean_counts.min."+binIndex;
				minMeanCounts[binIndex] = status.getPropertyInteger(propertyName);
				// best mean counts
				propertyName = LIST_KEY_STRING+"mean_counts.best."+binIndex;
				bestMeanCounts[binIndex] = status.getPropertyInteger(propertyName);
				// maximum mean counts
				propertyName = LIST_KEY_STRING+"mean_counts.max."+binIndex;
				maxMeanCounts[binIndex] = status.getPropertyInteger(propertyName);
			}// end for on binIndex
		}
		catch (Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":loadProperties:Failed to get property:"+propertyName);
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2300);
			twilightCalibrateDone.setErrorString(errorString);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		if(loadCalibrationList(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		if(loadOffsetList(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		if(loadState(twilightCalibrateCommand,twilightCalibrateDone) == false)
			return false;
		return true;
	}

	/**
	 * Method to load a list of calibrations to do. The list used depends on whether timeOfNight is set to
	 * sunrise or sunset.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #calibrationList
	 * @see #getFilterSensitivity
	 * @see #LIST_KEY_STRING
	 * @see #LIST_KEY_CALIBRATION_STRING
	 * @see #LIST_KEY_FILTER_STRING
	 * @see #LIST_KEY_BIN_STRING
	 * @see #LIST_KEY_FREQUENCY_STRING
	 * @see #LIST_KEY_SUNSET_STRING
	 * @see #LIST_KEY_SUNRISE_STRING
	 * @see #timeOfNight
	 */
	protected boolean loadCalibrationList(TWILIGHT_CALIBRATE twilightCalibrateCommand,
		TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		TWILIGHT_CALIBRATECalibration calibration = null;
		String timeOfNightString = null;
		String filter = null;
		int index,bin;
		long frequency;
		double filterSensitivity;
		boolean done;

		index = 0;
		done = false;
		calibrationList = new Vector();
		if(timeOfNight == TIME_OF_NIGHT_SUNSET)
			timeOfNightString = LIST_KEY_SUNSET_STRING;
		else
			timeOfNightString = LIST_KEY_SUNRISE_STRING;
		while(done == false)
		{
			filter = status.getProperty(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_FILTER_STRING);
			if(filter != null)
			{
			// create calibration instance
				calibration = new TWILIGHT_CALIBRATECalibration();
			// get parameters from properties
				try
				{
					frequency = status.getPropertyLong(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_FREQUENCY_STRING);
					bin = status.getPropertyInteger(LIST_KEY_STRING+LIST_KEY_CALIBRATION_STRING+
							timeOfNightString+index+LIST_KEY_BIN_STRING);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed at index "+index+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2301);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// set calibration data
				try
				{
					calibration.setBin(bin);
					calibration.setFilter(filter);
					calibration.setFrequency(frequency);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed to set calibration data at index "+index+
						":bin:"+bin+":filter:"+filter+":frequency:"+frequency+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2302);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// get filter sensitivity, and set calibration sensitivities
				try
				{
					filterSensitivity = getFilterSensitivity(filter);
					calibration.setFilterSensitivity(filterSensitivity);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadCalibrationList:Failed to set filter sensitivities at index "+
						index+":bin:"+bin+
						":filter:"+filter+":frequency:"+frequency+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2303);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// add calibration instance to list
				calibrationList.add(calibration);
			// log
				o.log(Logging.VERBOSITY_VERBOSE,
					"Command:"+twilightCalibrateCommand.getClass().getName()+
					":Loaded calibration:"+index+
					":bin:"+calibration.getBin()+
					":filter:"+calibration.getFilter()+
					":frequency:"+calibration.getFrequency()+".");
			}
			else
				done = true;
			index++;
		}
		return true;
	}

	/**
	 * Method to get the relative filter sensitivity of a filter.
	 * @param filterType The type name of the filter to find the sensitivity for.
	 * @return A double is returned, which is the filter sensitivity relative to no filter,
	 * 	and should be in the range 0 to 1.
	 * @exception IllegalArgumentException Thrown if the filter sensitivity returned from the
	 * 	property is out of the range 0..1.
	 * @exception NumberFormatException  Thrown if the filter sensitivity property is not a valid double.
	 */
	protected double getFilterSensitivity(String filterType) throws NumberFormatException, IllegalArgumentException
	{
		double filterSensitivity;

		filterSensitivity = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_FILTER_SENSITIVITY_STRING+
								filterType);
		if((filterSensitivity < 0.0)||(filterSensitivity > 1.0))
		{
			throw new IllegalArgumentException(this.getClass().getName()+
				":getFilterSensitivity failed:filter type "+filterType+
				" has filter sensitivity "+filterSensitivity+", which is out of range.");
		}
		return filterSensitivity;
	}

	/**
	 * Method to initialse twilightCalibrateState.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #twilightCalibrateState
	 * @see #stateFilename
	 */
	protected boolean loadState(TWILIGHT_CALIBRATE twilightCalibrateCommand,
				    TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
	// initialise and load twilightCalibrateState instance
		twilightCalibrateState = new TWILIGHT_CALIBRATESavedState();
		try
		{
			twilightCalibrateState.load(stateFilename);
		}
		catch (Exception e)
		{
			String errorString = new String(twilightCalibrateCommand.getId()+
				":loadState:Failed to load state filename:"+stateFilename);
			loci.error(this.getClass().getName()+":"+errorString,e);
			twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2304);
			twilightCalibrateDone.setErrorString(errorString);
			twilightCalibrateDone.setSuccessful(false);
			return false;
		}
		return true;
	}

	/**
	 * Method to load a list of telescope RA/DEC offsets. These are used to offset the telescope
	 * between frames of the same calibration.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. If false is returned the error
	 * 	data in twilightCalibrateDone is filled in.
	 * @see #offsetList
	 * @see #LIST_KEY_OFFSET_STRING
	 * @see #LIST_KEY_RA_STRING
	 * @see #LIST_KEY_DEC_STRING
	 */
	protected boolean loadOffsetList(TWILIGHT_CALIBRATE twilightCalibrateCommand,
					 TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{

		TWILIGHT_CALIBRATEOffset offset = null;
		String testString = null;
		int index;
		double raOffset,decOffset;
		boolean done;

		index = 0;
		done = false;
		offsetList = new Vector();
		while(done == false)
		{
			testString = status.getProperty(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_RA_STRING);
			if((testString != null))
			{
			// create offset
				offset = new TWILIGHT_CALIBRATEOffset();
			// get parameters from properties
				try
				{
					raOffset = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_RA_STRING);
					decOffset = status.getPropertyDouble(LIST_KEY_STRING+LIST_KEY_OFFSET_STRING+
							index+LIST_KEY_DEC_STRING);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadOffsetList:Failed at index "+index+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2305);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// set offset data
				try
				{
					offset.setRAOffset((float)raOffset);
					offset.setDECOffset((float)decOffset);
				}
				catch(Exception e)
				{
					String errorString = new String(twilightCalibrateCommand.getId()+
						":loadOffsetList:Failed to set data at index "+index+
						":RA offset:"+raOffset+":DEC offset:"+decOffset+".");
					loci.error(this.getClass().getName()+":"+errorString,e);
					twilightCalibrateDone.setErrorNum(LociConstants.LOCI_ERROR_CODE_BASE+2306);
					twilightCalibrateDone.setErrorString(errorString);
					twilightCalibrateDone.setSuccessful(false);
					return false;
				}
			// add offset instance to list
				offsetList.add(offset);
			// log
				loci.log(Logging.VERBOSITY_VERBOSE,
					 "Command:"+twilightCalibrateCommand.getClass().getName()+
					 ":Loaded offset "+index+
					 ":RA Offset:"+offset.getRAOffset()+
					 ":DEC Offset:"+offset.getDECOffset()+".");
			}
			else
				done = true;
			index++;
		}
		return true;
	}

	/**
	 * This method matches the saved state to the calibration list to set the last time
	 * each calibration was completed.
	 * @param twilightCalibrateCommand The instance of TWILIGHT_CALIBRATE we are currently running.
	 * @param twilightCalibrateDone The instance of TWILIGHT_CALIBRATE_DONE to fill in with errors we receive.
	 * @return The method returns true if it succeeds, false if it fails. Currently always returns true.
	 * @see #calibrationList
	 * @see #twilightCalibrateState
	 */
	protected boolean addSavedStateToCalibration(TWILIGHT_CALIBRATE twilightCalibrateCommand,
						     TWILIGHT_CALIBRATE_DONE twilightCalibrateDone)
	{
		TWILIGHT_CALIBRATECalibration calibration = null;
		String filter = null;
		int bin;
		long lastTime;

		for(int i = 0; i< calibrationList.size(); i++)
		{
			calibration = (TWILIGHT_CALIBRATECalibration)(calibrationList.get(i));
			bin = calibration.getBin();
			filter = calibration.getFilter();
			lastTime = twilightCalibrateState.getLastTime(bin,filter);
			calibration.setLastTime(lastTime);
			loci.log(Logging.VERBOSITY_VERBOSE,
				 "Command:"+twilightCalibrateCommand.getClass().getName()+":Calibration:"+
				 "bin:"+calibration.getBin()+
				 ":filter:"+calibration.getFilter()+
				 ":frequency:"+calibration.getFrequency()+
				 " now has last time set to:"+lastTime+".");
		}
		return true;
	}


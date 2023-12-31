/**
 * Phoenix Software License Agreement
 *
 * Copyright (C) Cross The Road Electronics.  All rights
 * reserved.
 * 
 * Cross The Road Electronics (CTRE) licenses to you the right to 
 * use, publish, and distribute copies of CRF (Cross The Road) firmware files (*.crf) and 
 * Phoenix Software API Libraries ONLY when in use with CTR Electronics hardware products
 * as well as the FRC roboRIO when in use in FRC Competition.
 * 
 * THE SOFTWARE AND DOCUMENTATION ARE PROVIDED "AS IS" WITHOUT
 * WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT
 * LIMITATION, ANY WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, TITLE AND NON-INFRINGEMENT. IN NO EVENT SHALL
 * CROSS THE ROAD ELECTRONICS BE LIABLE FOR ANY INCIDENTAL, SPECIAL, 
 * INDIRECT OR CONSEQUENTIAL DAMAGES, LOST PROFITS OR LOST DATA, COST OF
 * PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY OR SERVICES, ANY CLAIMS
 * BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY DEFENSE
 * THEREOF), ANY CLAIMS FOR INDEMNITY OR CONTRIBUTION, OR OTHER
 * SIMILAR COSTS, WHETHER ASSERTED ON THE BASIS OF CONTRACT, TORT
 * (INCLUDING NEGLIGENCE), BREACH OF WARRANTY, OR OTHERWISE
 */

/**
 * Description:
 * This Java FRC robot application is meant to demonstrate an example using the Motion Profile control mode
 * in Talon FX.  The TalonFX class gives us the ability to buffer up trajectory points and execute them
 * as the roboRIO streams them into the Talon FX.
 * 
 * There are many valid ways to use this feature and this example does not sufficiently demonstrate every possible
 * method.  Motion Profile streaming can be as complex as the developer needs it to be for advanced applications,
 * or it can be used in a simple fashion for fire-and-forget actions that require precise timing.
 * 
 * This application is a TimedRobot project to demonstrate a minimal implementation not requiring the command 
 * framework, however these code excerpts could be moved into a command-based project.
 * 
 * The project also includes instrumentation.java which simply has debug printfs, and a MotionProfile.java which is generated
 * in @link https://docs.google.com/spreadsheets/d/1PgT10EeQiR92LNXEOEe3VGn737P7WDP4t0CQxQgC8k0/edit#gid=1813770630&vpid=A1
 * or find Motion Profile Generator.xlsx in the Project folder.
 * 
 * Controls:
 * Button 5: When held, initialize motion pofile. Send Motion Profile to Talon while it is nuetral.
 * Button 6: If Button 5 is held, press to run motion profile, else do nothing. 
 * 	Hold final Motion Profile point until Motion Profile is fired again (Press Button 6 again)
 * Left Joystick Y-Axis: Throttle Talon FX forward and reverse when no buttons are pressed.
 * 
 * Gains for Motion Profile may need to be adjusted in Constants.java
 */

package frc.robot;

import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.Joystick;

import com.ctre.phoenix.motion.*;
import com.ctre.phoenix.motorcontrol.DemandType;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.StatusFrameEnhanced;
import com.ctre.phoenix.motorcontrol.TalonFXControlMode;
import com.ctre.phoenix.motorcontrol.TalonFXFeedbackDevice;
import com.ctre.phoenix.motorcontrol.TalonFXInvertType;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.sensors.CANCoder;
import com.ctre.phoenix.motorcontrol.can.TalonFXConfiguration;

import frc.robot.sim.PhysicsSim;

public class Robot extends TimedRobot {
	/** Hardware */
	WPI_TalonFX _talon = new WPI_TalonFX(11, "rio");	// Talon to Motion Profile
	WPI_TalonFX _talon2 = new WPI_TalonFX(12, "rio");	// Talon to Motion Profile
	CANCoder cancoder = new CANCoder(5);
	Joystick _joy = new Joystick(0);	// Joystick for testing

	/** Invert Directions for Left and Right */
	TalonFXInvertType _talonInvert = TalonFXInvertType.Clockwise; //Same as invert = "true"

	/** Config Objects for motor controllers */
	TalonFXConfiguration _talonConfig = new TalonFXConfiguration();

	/** Some example logic on how one can manage an MP */
	MotionProfileExample _example = new MotionProfileExample(_talon);
	int kMeasuredPosHorizontal = 670; //Position measured when arm is horizontal

	double kTicksPerDegree = 4096 / 360; //Sensor is 1:1 with arm rotation
	int currentPos = (int)_talon.getSelectedSensorPosition();
	double degrees = (currentPos - kMeasuredPosHorizontal) / kTicksPerDegree;
	double radians = java.lang.Math.toRadians(degrees);
	double cosineScalar = java.lang.Math.cos(radians);

	double maxGravityFF = 0.03;
	/**
	 * Cache last buttons so we can detect press events. In a command-based
	 * project you can leverage the on-press event but for this simple example,
	 * lets just do quick compares to prev-btn-states
	 */
	boolean[] _previousBtns = {	false, false, false, false, false, 
								false, false, false, false, false};
	
	public void simulationInit() {
		PhysicsSim.getInstance().addTalonFX(_talon, 0.5, 7200);
	}
	public void simulationPeriodic() {
		PhysicsSim.getInstance().run();
	}

	/** Run once after booting/enter-disable */
	public void disabledInit() {

		/* Configure Selected Sensor for Motion Profile */
		_talonConfig.primaryPID.selectedFeedbackSensor = TalonFXFeedbackDevice.RemoteSensor0.toFeedbackDevice();
		/*
			* Talon FX does not need sensor phase set for its integrated sensor
			* This is because it will always be correct if the selected feedback device is integrated sensor (default value)
			* and the user calls getSelectedSensor* to get the sensor's position/velocity.
			* 
			* https://phoenix-documentation.readthedocs.io/en/latest/ch14_MCSensor.html#sensor-phase
			*/
		// _talon.setSensorPhase(true);
		
		/**
		 * Configure MotorController Neutral Deadband, disable Motor Controller when
		 * requested Motor Output is too low to process
		 */
		_talonConfig.neutralDeadband = Constants.kNeutralDeadband;

		/* Configure PID Gains, to be used with Motion Profile */
		_talonConfig.slot0.kF = Constants.kGains.kF;
		_talonConfig.slot0.kP = Constants.kGains.kP;
		_talonConfig.slot0.kI = Constants.kGains.kI;
		_talonConfig.slot0.kD = Constants.kGains.kD;

		//32.5 degrees


		/* Our profile uses 10ms timing */
		_talonConfig.motionProfileTrajectoryPeriod = 10;

		_talon.configAllSettings(_talonConfig);

		_talon.setInverted(false);
		_talon.configRemoteFeedbackFilter(cancoder, 0);
		_talon.configSelectedFeedbackSensor(FeedbackDevice.RemoteSensor0);
		//_talon.setSelectedSensorPosition(0, 0, 20);
		

	    _talon.setSensorPhase(true);
		_talon2.setInverted(true);
		
		/* Status 10 provides the trajectory target for motion profile AND motion magic */
		_talon.setStatusFramePeriod(StatusFrameEnhanced.Status_10_MotionMagic, 10, Constants.kTimeoutMs);
		cancoder.configFactoryDefault();
	}

	/** 
	 * Function is called periodically during operator control  
	 */
	public void teleopPeriodic() {
		SmartDashboard.putNumber("i_am_being_held_hostage_by_jack_o_covert", _talon.getSelectedSensorPosition());
		/* Get buttons */
		boolean[] btns = new boolean[_previousBtns.length];
		for (int i = 1; i < _previousBtns.length; ++i)
			btns[i] = _joy.getRawButton(i);

		/**
		 * Get the left joystick axis on Logitech Gampead, 
		 * Joystick forward should be positive 
		 */
		double leftYjoystick = -1 * _joy.getY();

		/**
		 * Call this periodically, and catch the output. 
		 * Only apply it if user wants to run MP.
		 */
		_example.control();

		/* Check button 5 (top left shoulder on the logitech gamead). */
		if (btns[5] == false) {
			/**
			 * If it's not being pressed, just do a simple drive. This could be
			 * a RobotDrive class or custom drivetrain logic. The point is we
			 * want the switch in and out of MP Control mode.
			 */
			_talon.set(TalonFXControlMode.PercentOutput, leftYjoystick*0.05);

			_example.reset();
		} else {
			/**
			 * When button 5 is held down so switch to motion profile control mode =>
			 * This is done in MotionProfileControl. When we transition from
			 * no-press to press, pass a "true" once to MotionProfileControl.
			 */
			SetValueMotionProfile setOutput = _example.getSetValue();

			_talon.set(TalonFXControlMode.MotionProfile, setOutput.value, DemandType.ArbitraryFeedForward, maxGravityFF * cosineScalar);

			/**
			 * If button 6 is pressed and was not pressed last time, In other words
			 * we just detected the on-press event. This will signal the robot
			 * to start a MP
			 */
			if ((btns[6] == true) && (_previousBtns[6] == false)) {
				// --- We could start a MP if MP isn't already running ----//
				cancoder.setPosition(0);
				_example.startMotionProfile();
			}
		}

		/* Save buttons states for on-press detection */
		for (int i = 1; i < btns.length; ++i)
			_previousBtns[i] = btns[i];
	}

	/** 
	 * Function is called periodically during disable 
	 */
	public void disabledPeriodic() {
		/**
		 * It's generally a good idea to put motor controllers back into a known
		 * state when robot is disabled. That way when you enable the robot
		 * doesn't just continue doing what it was doing before. BUT if that's
		 * what the application/testing requires than modify this accordingly
		 */
		_talon.set(TalonFXControlMode.PercentOutput, 0);

		/* Clear our buffer and put everything into a known state */
		_example.reset();
	}
}

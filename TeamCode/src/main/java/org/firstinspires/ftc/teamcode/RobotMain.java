//test commit

package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.matrices.OpenGLMatrix;
import org.firstinspires.ftc.robotcore.external.matrices.VectorF;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackable;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackableDefaultListener;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackables;
import org.firstinspires.ftc.robotcore.external.tfod.Recognition;
import org.firstinspires.ftc.robotcore.external.tfod.TFObjectDetector;
import org.firstinspires.ftc.teamcode.lib.FieldCenterPosition;
import org.firstinspires.ftc.teamcode.subsystems.*;

import com.qualcomm.robotcore.hardware.Gyroscope;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.CRServo;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.robotcore.util.ElapsedTime;

import java.util.ArrayList;
import java.util.List;

import static org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.DEGREES;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesOrder.XYZ;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesOrder.YZX;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesReference.EXTRINSIC;

public class RobotMain {

    //Declare hardwareMap
    public static HardwareMap hardwareMap;

    //Declare gamepads
    public static Gamepad gamepad1;
    public static Gamepad gamepad2;

    //Declare all & put in ArrayList
    public static Subsystem driveTrain = DriveTrain.getInstance();
    public static Subsystem elevatingArm = ElevatingArm.getInstance();
    public static Subsystem gripper = Gripper.getInstance();
    public static Subsystem[] allSubsystems = {driveTrain, elevatingArm, gripper};

    //Declare eemuu
    public static BNO055IMU gyro;

    //Declare vision & object dection engines
    private VuforiaLocalizer vuforia;
    private TFObjectDetector tfod;

    //Declare Vuforia members
    public static final String VUFORIA_KEY = "Aa4qojf/////AAABmUtRp+oA10Tyg9NdvwIzzH4eVE09jioK/9lv2fPHeJLN4mXBj/AfGpZM/0ym7+uvZfeSNpIhhU3UJ" +
            "tFl9JRatjump2m7urI4tq+M1FtU/sEdTD4uHJjGuoI4HW7BTvLvxNxuEQZ3f3sexDW8F8FJPOkkJHnbycwT1m+h7EQqjnwiySsMWeKoN/Fu2cGljvuZ5LAGpVVosB2" +
            "plP1dtviSkJbGy7MsHmJjL/NqEv/fjuiFqlra9Y29n8oZRoDsvwJkHJw/oQIv4kpTRHMSKV6NZZeyRm46zsb7mFkW0yXDpANgWqCjvAJVPm5W5JTcq8IZqDUn0bJyQ" +
            "Ju/F0OFbg2JsKHBcxNA1hasVyUxPrlIQCYc";
    List<VuforiaTrackable> allTrackables = new ArrayList<VuforiaTrackable>();

    public static final float mmPerInch = 25.4f;
    private static final float mmTargetHeight = 6 * mmPerInch; //Height of the center of the target image above the floor
    private static final float stoneZ = 2.00f * mmPerInch;
    private static final float halfField = 72 * mmPerInch;
    private static final float quadField  = 36 * mmPerInch;

    //TODO fill in these values for our robot camera relative to center
    final float CAMERA_FORWARD_DISPLACEMENT = 0.0f * mmPerInch;
    final float CAMERA_VERTICAL_DISPLACEMENT = 0.0f * mmPerInch;
    final float CAMERA_LEFT_DISPLACEMENT = 0.0f * mmPerInch;

    private OpenGLMatrix lastLocation = null;
    private boolean targetVisible = false;

    //TODO fill in these values for our robot camera
    private float phoneXRotate = 0;
    private float phoneYRotate = 0;
    private float phoneZRotate = 0;

    //Declare Tensor Flow members
    private static final String TFOD_MODEL_ASSET = "Skystone.tflite";
    private static final String LABEL_STONE = "Stone";
    private static final String LABEL_SKYSTONE = "Skystone";
    private static final double MINIMUM_CONFIDENCE = 0.75;

    //Declare misc objects
    private ElapsedTime timer;

    public RobotMain(HardwareMap hardwareMap, Gamepad gamepad1, Gamepad gamepad2) {
        this.hardwareMap = hardwareMap;
        this.gamepad1 = gamepad1;
        this.gamepad2 = gamepad2;
        robotInit();
    }

    private void robotInit() {
        //Init all subsystems
        for (Subsystem subsystem : allSubsystems) {
            subsystem.subsystemInit(hardwareMap);
        }

        //Init eemuu
        gyro = (BNO055IMU) hardwareMap.get(Gyroscope.class, "imu");

        //Init vision
        initVuforia();
        initTfod();

        //Init misc objects
        timer = new ElapsedTime();
    }

    //For if regular framework stops working
    public DcMotor getDcMotor(String name) { return hardwareMap.get(DcMotor.class, name); }

    public Servo getServoMotor(String name) { return hardwareMap.get(Servo.class, name); }

    public CRServo getCRServoMotor(String name) { return hardwareMap.get(CRServo.class, name); }

    //Vision
    public void initVuforia() {
        //Init vuforia engine
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        VuforiaLocalizer.Parameters parameters = new VuforiaLocalizer.Parameters(cameraMonitorViewId);
        parameters.vuforiaLicenseKey = RobotMain.VUFORIA_KEY;
        parameters.cameraDirection = VuforiaLocalizer.CameraDirection.BACK;
        vuforia = ClassFactory.getInstance().createVuforia(parameters);

        //Init all trackable objects
        VuforiaTrackables targetsSkyStone = vuforia.loadTrackablesFromAsset("Skystone");

        //Perimeter targets
        VuforiaTrackable red1 = targetsSkyStone.get(5);
        red1.setName("Red Perimeter 1");
        VuforiaTrackable red2 = targetsSkyStone.get(6);
        red2.setName("Red Perimeter 2");
        VuforiaTrackable front1 = targetsSkyStone.get(7);
        front1.setName("Front Perimeter 1");
        VuforiaTrackable front2 = targetsSkyStone.get(8);
        front2.setName("Front Perimeter 2");
        VuforiaTrackable blue1 = targetsSkyStone.get(9);
        blue1.setName("Blue Perimeter 1");
        VuforiaTrackable blue2 = targetsSkyStone.get(10);
        blue2.setName("Blue Perimeter 2");
        VuforiaTrackable rear1 = targetsSkyStone.get(11);
        rear1.setName("Rear Perimeter 1");
        VuforiaTrackable rear2 = targetsSkyStone.get(12);
        rear2.setName("Rear Perimeter 2");

        //All trackables in one collection
        allTrackables.addAll(targetsSkyStone);

        /*
         * Transformation matricies -- tell tell the system where each target is on the field, and
         * where the phone resides on the robot (OpenGLMatrix class)
         *
         * If standing in the Red Alliance Station looking towards the center of the field:
         *     - X axis runs from left to the right (positive from the center to the right)
         *     - Y axis runs from Red Alliance Station to other side of field (Blue Alliance Station)
         *       (positive from center towards the BlueAlliance station)
         *     - Z axis runs from floor to ceiling (positive is above the floor)
         *
         * Assume that before being transformed, each target image is conceptually located at the origin of the field's
         * coordinate system (the center of the field) facing up.
         */

        //Set position & rotation of perimeter targets with relation to origin
        red1.setLocation(OpenGLMatrix
                .translation(quadField, -halfField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 180)));

        red2.setLocation(OpenGLMatrix
                .translation(-quadField, -halfField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 180)));

        front1.setLocation(OpenGLMatrix
                .translation(-halfField, -quadField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0 , 90)));

        front2.setLocation(OpenGLMatrix
                .translation(-halfField, quadField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 90)));

        blue1.setLocation(OpenGLMatrix
                .translation(-quadField, halfField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 0)));

        blue2.setLocation(OpenGLMatrix
                .translation(quadField, halfField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, 0)));

        rear1.setLocation(OpenGLMatrix
                .translation(halfField, quadField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0 , -90)));

        rear2.setLocation(OpenGLMatrix
                .translation(halfField, -quadField, mmTargetHeight)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, 90, 0, -90)));

        //Rotate camera around long axis for back camera
        phoneYRotate = -90;

        //Declare camera position as a matrix
        OpenGLMatrix robotFromCamera = OpenGLMatrix
                .translation(CAMERA_FORWARD_DISPLACEMENT, CAMERA_LEFT_DISPLACEMENT, CAMERA_VERTICAL_DISPLACEMENT)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, YZX, DEGREES, phoneYRotate, phoneZRotate, phoneXRotate));

        //Enable all the listeners
        for (VuforiaTrackable trackable : allTrackables) {
            ((VuforiaTrackableDefaultListener) trackable.getListener()).setPhoneInformation(robotFromCamera, parameters.cameraDirection);
        }
        targetsSkyStone.activate();
    }

    public void initTfod() {
        int tfodMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("tfodMonitorViewId",
                "id", hardwareMap.appContext.getPackageName());
        TFObjectDetector.Parameters tfodParameters = new TFObjectDetector.Parameters(tfodMonitorViewId);
        tfodParameters.minimumConfidence = MINIMUM_CONFIDENCE;
        tfod = ClassFactory.getInstance().createTFObjectDetector(tfodParameters, vuforia);
        tfod.loadModelFromAsset(TFOD_MODEL_ASSET, LABEL_STONE, LABEL_SKYSTONE);
    }

    public FieldCenterPosition getFIeldPositionFromPerimeterTarget() {
        //Check all perimeter trackables to see if one is found
        targetVisible = false;
        for (VuforiaTrackable trackable : allTrackables) {
            if (((VuforiaTrackableDefaultListener)trackable.getListener()).isVisible()) {
                targetVisible = true;

                //getUpdatedRobotLocation() will return null if no new information is available or if the trackable is not currently visible.
                OpenGLMatrix robotLocationTransform = ((VuforiaTrackableDefaultListener) trackable.getListener()).getUpdatedRobotLocation();
                if (robotLocationTransform != null) {
                    lastLocation = robotLocationTransform;
                }
                break;
            }
        }

        //Provide feedback as to where the robot is located relative to trackable
        FieldCenterPosition fieldCenterPosition = new FieldCenterPosition(null, null);;
        if (targetVisible) {
            //Express translation and rotation of robot as a FieldCenterPosition object
            VectorF translation = lastLocation.getTranslation();
            Orientation orientation = Orientation.getOrientation(lastLocation, EXTRINSIC, XYZ, DEGREES);
            fieldCenterPosition = new FieldCenterPosition(translation, orientation);
        }
        return fieldCenterPosition;
    }

    /**
     * The starting skystone configuration at the beginning of autonomous can be in one of three states:
     *        1. SS -- -- SS -- --
     *        2. -- SS -- -- SS --
     *        3. -- -- SS -- -- SS
     * SS represents a skystone, while -- represents a regular stone.
     *
     * @return Starting skystone config. Will return -1 if cannot be sensed.
     */
    //TODO finish method
    public int getSkystonePosition(boolean enableTimer) {
        if (tfod != null) {
            //Activate tfod
            tfod.activate();

            //Get all recognitions & filter out unwanted (within 5 seconds)
            if (enableTimer) {
                timer.reset();
            }

            //Get all recognitions & filter out unwanted
            List<Recognition> updatedRecognitions = tfod.getUpdatedRecognitions();
            if (updatedRecognitions != null) {
                for (int i = updatedRecognitions.size() - 1; i >= 0; i--) {
                    if (!updatedRecognitions.get(i).getLabel().equals(LABEL_SKYSTONE)) {
                        updatedRecognitions.remove(i);
                    }
                }
            }

            //Shutdown tfod
            tfod.shutdown();
        }

        return 0;
    }

    //Gyro control
    public static double getAngle() {
        //Retrieve raw angle
        Orientation angles = gyro.getAngularOrientation(AxesReference.INTRINSIC, AxesOrder.ZYX, AngleUnit.DEGREES);
        double rawAngle = angles.firstAngle;

        //Manipulate angle to unit circle convention (degrees)
        if (rawAngle < 0) {
            rawAngle = 360 + rawAngle;
        }

        //Orient to unit circle convention (degrees)
        rawAngle -= 90;
        if (rawAngle < 0) {
            rawAngle += 360;
        }

        return rawAngle;
    }
}

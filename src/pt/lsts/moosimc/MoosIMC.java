package pt.lsts.moosimc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JFileChooser;

import com.github.moos_ivp.geodesy.LocalCoord;
import com.github.moos_ivp.geodesy.MOOSGeodesy;

import MOOS.MOOSCommClient;
import MOOS.MOOSMsg;
import MOOS.MoosMessageHandler;
import MOOS.comms.MessageType;
import pt.lsts.imc.DesiredHeading;
import pt.lsts.imc.DesiredSpeed;
import pt.lsts.imc.FollowRefState;
import pt.lsts.imc.FollowReference;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.PlanControl;
import pt.lsts.imc.PlanControl.OP;
import pt.lsts.imc.PlanControl.TYPE;
import pt.lsts.imc.PlanControlState.STATE;
import pt.lsts.imc.PlanManeuver;
import pt.lsts.imc.PlanSpecification;
import pt.lsts.imc.Reference;
import pt.lsts.imc.adapter.VehicleAdapter;
import pt.lsts.imc.def.SpeedUnits;
import pt.lsts.imc.net.Consume;
import pt.lsts.neptus.messages.listener.Periodic;

public class MoosIMC extends VehicleAdapter implements MoosMessageHandler {

	private MOOSCommClient client = new MOOSCommClient("localhost", 9000);
	private final double originLat, originLong; // reference used to XY offsets in bhv file
	private final String hostname;
	private Reference goal = new Reference();
	private final static int imcid = 0x00D7; // FIXME
	private MOOSGeodesy geoInstance = new MOOSGeodesy();
	private LogManagment logger;
	private final double capture_radius;
	

	ConcurrentHashMap<String, Double> ivpState = new ConcurrentHashMap<>();
	boolean deployed = false, returning = false;
	ExecutionStateAdapter planManager = new ExecutionStateAdapter();

	public MoosIMC(String name, double la, double lo,double radius, String moos_path) {
		super(name, imcid);
		hostname = name;
		originLat = la;
		originLong = lo;
		capture_radius = radius;
		System.err.println("Before");
		if (!geoInstance.initialize(originLat, originLong)) {
			System.err.println(
					"Unnable to initialize Geodesy Instance, please check project setup and initial coordinates.");
			return;
		}
		// logger = new LogManagment(moos_path, name);
		System.err.println("GOT here1");
		client.setAutoReconnect(true);
		client.setEnable(true);
		client.setMessageHandler(this);

		client.register("LatOrigin", 0.0);
		client.register("LongOrigin", 0.0);
		client.register("DESIRED_SPEED", 0.0);
		client.register("DESIRED_HEADING", 0.0);
		client.register("NAV_LAT", 0.0);
		client.register("NAV_LONG", 0.0);
		client.register("NAV_ROLL", 0.0);
		client.register("NAV_PITCH", 0.0);
		client.register("NAV_HEADING", 0.0);
		client.register("NAV_SPEED", 0.0);
		client.register("NAV_DEPTH", 0.0);
		client.register("NAV_YAW", 0.0);
		client.register("PARK", 0.0);
		client.register("DEPLOY", 0.0);
		client.register("RETURN", 0.0);
		client.register("BHV_STATUS", -1.0);
		client.register("WPT_STAT", -1.0);
		client.register("MODE", -1.0);
		client.register("DIST_TO_DESTINATION", 0.0);
		client.register("FOLLOW_UPDATE", -1.0); // FIXME initialize string vars properly

//		Runtime.getRuntime().addShutdownHook(new Thread() {
//			@Override
//			public void run() {
//				// logger.shutdown();
//				// FIXME close IMCProtocol instance
//			}
//		});

	}

	@Consume
	@Override
	public void on(PlanControl pc) {
		if (pc.getPlanId().equalsIgnoreCase("follow_neptus")) {
			PlanControl reply = new PlanControl();
			reply.setSrc(imcid);
			if (pc.getType() == TYPE.REQUEST && pc.getOp() == OP.START) {
				// client.notify(new MOOSMsg(MessageType.Notify, "DEPLOY", "true"));

				client.notify(new MOOSMsg(MessageType.Notify, "DEPLOY", "true"));
				inf("Activating follow_neptus behaviour");
				super.on(pc);

				planControl.setPlanId(pc.getPlanId());
				planControl.setState(STATE.EXECUTING);
				reply.setType(PlanControl.TYPE.SUCCESS);
				if (pc.getArg() != null) {
					PlanSpecification ps = new PlanSpecification(pc.getArg());
					for (PlanManeuver m : ps.getManeuvers()) {
						if (m.getData().getAbbrev().equalsIgnoreCase("FollowReference")) {
							FollowReference followR = m.getData().cloneMessageTyped();
							goal.setLat(originLat);
							goal.setLon(originLong);
							goal.setRadius(followR.getLoiterRadius());
							goal.setSrc(followR.getSrc());
							goal.setSrcEnt(followR.getSrcEnt());
							goal.setFlags(goal.FLAG_LOCATION);
						}

					}
				}
				dispatch(reply);
				FollowRefState fstate = new FollowRefState(pc.getSrc(), pc.getSrcEnt(), goal,
						pt.lsts.imc.FollowRefState.STATE.WAIT, FollowRefState.PROX_FAR);
				dispatch(fstate);

				PlanSpecification ps = super.planDbManager.getSpec(pc.getPlanId());
				if (!planManager.startPlan(ps))
					war("Error Starting Plan: " + pc.getPlanId());
			}
			if (pc.getType() == TYPE.REQUEST && pc.getOp() == OP.STOP) {
				planControl.setPlanId("");
				planControl.setState(STATE.READY);
				planManager.finishPlan(false);
				reply.setType(PlanControl.TYPE.SUCCESS);
				dispatch(reply);
			}
		} else
			super.on(pc);
	}

	/**
	 * FOLLOW_UPDATE = "points=50,50 # speed = 1.5"
	 * 
	 * @param ref
	 */
	@Consume
	public void on(Reference ref) {
		inf("Got reference towards: " + ref.getLat() + " " + ref.getLon());
		goal = new Reference(ref.cloneMessage());
		StringJoiner params = new StringJoiner(" # "); // parameter = value # parameter = value # ... # parameter =
														// value
		// dist to target
		inf("Ref: " + ref.asJSON() + "\n\n");
		double lat = Math.toDegrees(ref.getLat()), lon = Math.toDegrees(ref.getLon());
		LocalCoord localC = geoInstance.latLon2LocalGrid(lat, lon);

		StringJoiner param = new StringJoiner(",", "points=", "");
		param.add(new Double(localC.getX()).toString());
		param.add(new Double(localC.getY()).toString());
		params.add(param.toString());
		param = new StringJoiner(",", " speed=", "");
		if (ref.getSpeed() != null) {
			if (ref.getSpeed().getSpeedUnits() == SpeedUnits.METERS_PS) {
				param.add(new Double(ref.getSpeed().getValue()).toString()); // m/s
				params.add(param.toString());
				param = new StringJoiner(",", " radius=", "");
				param.add(new Double(ref.getRadius()).toString());
			} else
				war("Maneuver not implemented in system for speed unit: " + ref.getSpeed().getSpeedUnitsStr());
		}
		if (ref.getZ() != null) {
			// TODO
		}
		client.notify(new MOOSMsg(MessageType.Notify, "FOLLOW_UPDATE", params.toString())); // FOLLOW_UPDATE
	}

	@Periodic(1000)
	public void sendDesiredSpeed() {
		DesiredSpeed speed = new DesiredSpeed();
		speed.setSpeedUnits(SpeedUnits.METERS_PS);
		synchronized (ivpState) {
			speed.setValue(ivpState.get("DESIRED_SPEED"));
		}
		dispatch(speed);
	}

	@Periodic(1000)
	public void sendDesiredHeading() {
		DesiredHeading msg = new DesiredHeading();
		synchronized (ivpState) {
			double d = ivpState.get("DESIRED_HEADING");
			if (d >= 360) {
				System.err.println("D before: " + d);
				d = d - (360 * (d / 360));
			}
			double rads = Math.toRadians(d);
			msg.setValue(rads);
			setEuler(0, 0, d);
		}
		dispatch(msg);
	}

//	@Periodic(1000)
//	public void sendPlanControlState() {
//		synchronized (ivpState) {
//			planControl.setState(STATE.READY);
//			if (deployed) {
//				planControl.setState(STATE.EXECUTING);
//				planControl.setPlanId("moos plan");
//			}
//			if (returning) {
//				planControl.setState(STATE.BLOCKED);
//				planControl.setPlanId("moos return");
//			}
//		}
//	}

	@Periodic(500)
	public void sendState() {
		synchronized (ivpState) {
			try {
				setPosition(ivpState.get("NAV_LAT"), ivpState.get("NAV_LONG"), 0, ivpState.get("NAV_DEPTH"));
				setEuler(0, ivpState.get("NAV_PITCH"), ivpState.get("NAV_HEADING"));
				// FIXME log state messages (generated in inherited class)

			} catch (Exception e) {
				err("Error retrieving data: " + e.getMessage());
				System.err.println("Error retrieving data: " + e.getMessage());
			}
		}
	}

	public static void main(String[] args) throws IOException {
		// new MoosIMC("lauv-moos-1", 43.825300, -70.330400, ""); // enter hostname in
		String path = null;
		if (args.length == 1) {
			File moos_file = new File(args[0]);
			if (moos_file.exists()) {
				path = moos_file.getAbsolutePath();
			}
		} else {
			JFileChooser jc = new JFileChooser();
			if (jc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
				path = jc.getSelectedFile().getAbsolutePath();
			} else {
				System.err.println("Please provide the moos file path.");
				return;
			}
		}
		double[] params = MoosParser.getParams(Files.readAllLines(Paths.get(path))); // initial lat and long set to 0.0 if not defined in bhv file default radius 1.0
		new MoosIMC("lauv-moos-1", params[0], params[1],params[2], path); // enter hostname in args
		System.err.println("Finished instance creation");
	}

	@Override
	public boolean handleMessages(ArrayList<MOOSMsg> messages) {
		messages.stream().forEachOrdered(msg -> {
			double dist = -1;
			List<IMCMessage> states; 
			String state_bhv="",state_wp = "",warns="",state_helm="";
			synchronized (ivpState) {
				if (msg.isDouble()) {
					System.err.println(msg.getKey()+": "+msg.getDoubleData());
					ivpState.put(msg.getKey(), msg.getDoubleData());
				}
				if (ivpState.containsKey("SURVEY_DIST_TO_NEXT"))
					dist = ivpState.get("SURVEY_DIST_TO_NEXT");
				else if (msg.getKey().equals("DEPLOY")) {
					deployed = msg.getStringData().equals("true");
				}
				else if (msg.getKey().equals("RETURN")) {
					returning = msg.getStringData().equalsIgnoreCase("true");
				}
				else if (msg.getKey().equals("WPT_STAT")) {
					state_wp = msg.getStringData();
				} else if (msg.getKey().equals("BHV_STATUS")) {
					state_bhv = msg.getStringData();
				} else if (msg.getKey().equals("IVPHELM_STATE")) {
					state_helm = msg.getStringData();
				} else if (msg.getKey().equals("BHV_WARNING")) {
					warns = msg.getStringData();
				}
				states = planManager.planControllerState(planControl.getPlanId(),state_wp, dist, goal);
				states.addAll(planManager.pathControllerState(state_bhv,dist,ivpState.get("NAV_LAT").doubleValue(),ivpState.get("NAV_LONG").doubleValue(),ivpState.get("NAV_DEPTH").floatValue(),goal));
			}
			if (states.size() > 0)
				for (IMCMessage m : states)
					dispatch(m); // FollowRefState | PlanControlState | PathControlState | DesiredPath //TODO log messages
		});
		return true;
	}
}

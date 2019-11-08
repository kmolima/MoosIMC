package pt.lsts.moosimc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;

import pt.lsts.imc.Abort;
import pt.lsts.imc.DesiredPath;
import pt.lsts.imc.FollowRefState;
import pt.lsts.imc.IMCMessage;
import pt.lsts.imc.PathControlState;
import pt.lsts.imc.FollowRefState.STATE;
import pt.lsts.imc.PlanControlState;
import pt.lsts.imc.PlanControlState.LAST_OUTCOME;
import pt.lsts.imc.PlanManeuver;
import pt.lsts.imc.PlanSpecification;
import pt.lsts.imc.Reference;
import pt.lsts.imc.def.ZUnits;
import pt.lsts.imc.net.Consume;
import pt.lsts.util.WGS84Utilities;

public class ExecutionStateAdapter {
	private boolean maneuvering;
	private long pathcontroller;
	private DesiredPath prevPath;
	
	
	
	public ExecutionStateAdapter() {
		maneuvering = false;
		pathcontroller = 0;
		prevPath = null;
	}
	
	@Consume
	public void on(Abort msg){
		
	}
	
	public List<IMCMessage> planControllerState (String planid, String status, double dist, Reference ref) {
		List<IMCMessage> msgs = new ArrayList<>();
		//System.out.println("Parsing State: "+status);
		if(!maneuvering)
			return msgs;
		//FollowRefState
		String [] params = status.split(",");
		String bhv = params[1].split("=")[1];
		System.err.println("Behavior Name: "+bhv);
		System.err.println("Plan id: "+planid);
		Scanner scanner = new Scanner(status);
		int index = scanner.nextInt(); //TODO convert to man_eta in IMC
		scanner.close();
		scanner = new Scanner(params[params.length]);
		int man_eta = scanner.nextInt();
		scanner.close();
		if(planid.equals(bhv))
		{
			System.out.println("Plan id and bhv match!");
			PlanControlState pcs = new PlanControlState(pt.lsts.imc.PlanControlState.STATE.EXECUTING, bhv, -1, -1, "1",-1, man_eta, status.contains("completed")? LAST_OUTCOME.SUCCESS : LAST_OUTCOME.NONE);
			if(status.contains("completed"))
				finishPlan(true);
			else
				finishPlan(false);
			msgs.add(pcs);
		}
		if(bhv.equalsIgnoreCase("follow_neptus")) {
			FollowRefState st = new FollowRefState(ref.getSrc(), ref.getSrcEnt(), ref, STATE.HOVER, FollowRefState.PROX_XY_NEAR );
			msgs.add(st);
			
		}
		return msgs;
			
	}
	
	/**
	 * Finished plan execution
	 * @param b if the execution was successfully or not
	 */
	public void finishPlan(boolean b) {
		maneuvering = false;
		prevPath = null;
		
	}

	public boolean startPlan(PlanSpecification ps) {
			createIVPmission(ps);
			maneuvering = true;
			return true;
	}
	
	private void createIVPmission(PlanSpecification imcMessage) {
		// TODO Auto-generated method stub
//		for(PlanManeuver pman: imcMessage.getManeuvers()) {
//			//pman.
//		}
		
	}

	public Collection<? extends IMCMessage> pathControllerState(String state_bhv, double dist, double lat,
			double lon, float z, Reference ref) {
		List<IMCMessage> msgs = new ArrayList<>();
		if(!maneuvering)
			return msgs;
		
		float desiredz = ref.getZ()!= null ? ref.getZ().getFloat("value") : 0;
		ZUnits desiredzU = ref.getZ()!= null ? ref.getZ().getZUnits() : ZUnits.NONE;
		int eta = 65535; //FIXME //TODO
		
		DesiredPath dPath =  new DesiredPath();
		dPath.setEndLat(ref.getLat());
		dPath.setEndLon(ref.getLon());
		dPath.setEndZ(desiredz);
		dPath.setEndZUnits(desiredzU);
		dPath.setLradius(ref.getRadius());
		
		
		if(prevPath == null && ref != null) {
			pathcontroller++;
			dPath.setPathRef(pathcontroller);
			dPath.setStartLat(lat);
			dPath.setStartLon(lon);
			dPath.setStartZ(z);
			dPath.setStartZUnits(ZUnits.DEPTH);
			prevPath = dPath.cloneMessageTyped();
			msgs.add(dPath);
		}
		else if(prevPath != null ) {
			if(prevPath.getEndLat()!=ref.getLat() || prevPath.getEndLon()!=ref.getLon() ||  prevPath.getEndZ() != desiredz || !prevPath.getEndZUnits().equals(desiredzU)) { //FIXME check cases when z is not filled
				pathcontroller++;
				dPath.setStartLat(lat);
				dPath.setStartLon(lon);
				dPath.setStartZ(z);
				dPath.setStartZUnits(ZUnits.DEPTH);
				dPath.setPathRef(pathcontroller);
				msgs.add(dPath);
				prevPath = dPath;
			}
			else {
				dPath.setStartLat(prevPath.getStartLat());
				dPath.setStartLon(prevPath.getStartLon());
				dPath.setStartZ(prevPath.getStartZ());
				dPath.setStartZUnits(prevPath.getStartZUnits());
			}
			if(lat==ref.getLat() && lon==ref.getLon() &&  z == desiredz && ZUnits.DEPTH.equals(desiredzU)) {
			}
				
		}
		
		float x=0, y=0, zz = 0, vx=0, vy=0,vz=0;
//		double[]  displacements = WGS84Utilities.WGS84displacement(prevPath.getStartLat(), prevPath.getStartLon(), prevPath.getStartZ(), lat, lon, z);
//		x = new Float(displacements[0]); 
//		y = new Float((float) displacements[1]); 
//		z = new Float(displacements[2]); 
		
		int course_error=0;
		PathControlState pcst = new PathControlState(pathcontroller, lat, lon, z, ZUnits.DEPTH, ref.getLat(), ref.getLon(), desiredz, desiredzU, (float) ref.getRadius(), eta==0? PathControlState.FL_NEAR:0, x, y, zz, vx, vy, vz, course_error, eta);
		msgs.add(pcst);
		return msgs;
	}
	
}



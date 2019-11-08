package pt.lsts.moosimc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MoosParser {
	
	private static final String LAT_KEYWORD = "LatOrigin";
	private static final String LONG_KEYWORD = "LongOrigin";
	private static final String RADIUS_KEYWORD = "radius";
	private static final String C_RADIUS_KEYWORD = "capture_radius";


	
	static Map parseStatePairs(String sentence) {
		Map pairs = new HashMap();
		return pairs;
		
	}
	
	static double[] getParams(List<String> list) {
		double [] parameters = new double[3];

		for(String s: list) {
			if(s.startsWith(LAT_KEYWORD)) {
				parameters[0] = getDouble(s,"=");
				if(parameters[0] == Double.NaN)
					parameters[0] = 0.0;
			}
			else if(s.startsWith(LONG_KEYWORD)) {
				parameters[1] = getDouble(s,"=");
				if(parameters[1] == Double.NaN)
					parameters[1] = 0.0;
			}
			else if(s.startsWith(RADIUS_KEYWORD) || s.startsWith(C_RADIUS_KEYWORD)) {
				parameters[2] = getDouble(s,"=");
				if(parameters[2] != Double.NaN)
					parameters[2] = 2.0;
			}
		}
		
		return parameters;
	}

	/**
	 * @param coords
	 * @param s
	 */
	private static double getDouble(String s,String regex) {
		String[] params;
		params = s.split(regex);
		if(params.length > 1) {
			return new Double(params[1]);
		}
		return Double.NaN;
	}

}

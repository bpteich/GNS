package edu.umass.cs.gigapaxos;

import java.io.IOException;

import edu.umass.cs.reconfiguration.AbstractReconfiguratorDB;
import edu.umass.cs.utils.Config;
import edu.umass.cs.utils.MultiArrayMap;

/**
 * @author arun
 * 
 *         A container class for storing gigapaxos config parameters as an enum.
 */
public class PaxosConfig {
	/**
	 * Default file name for gigapaxos config parameters.
	 */
	public static final String DEFAULT_GIGAPAXOS_CONFIG_FILE = "gigapaxos.properties";
	/**
	 * Gigapaxos config file information can be specified using
	 * -DgigapaxosConfig=<filename> as a JVM argument.
	 */
	public static final String GIGAPAXOS_CONFIG_FILE_KEY = "gigapaxosConfig";

	/**
	 * Loads from a default file or file name specified as a system property. We
	 * take a type argument so that ReconfigurationConfig.RC can also mooch off
	 * the same properties file.
	 * 
	 * @param type
	 */
	public static void load(Class<?> type) {
		try {
			Config.register(type, GIGAPAXOS_CONFIG_FILE_KEY,
					DEFAULT_GIGAPAXOS_CONFIG_FILE);
		} catch (IOException e) {
			// ignore as default will still be used
		}
	}

	/**
	 * By default, PaxosConfig.PC will be registered.
	 */
	public static void load() {
		load(PC.class);
	}
	
	static {
		load();
	}

	/**
	 * All gigapaxos config parameters that can be specified via a configuration
	 * file.
	 */
	public static enum PC implements Config.DefaultValueEnum {
		/**
		 * Verbose debugging and request instrumentation
		 */
		DEBUG(false),
		/**
		 * True means no persistent logging
		 */
		DISABLE_LOGGING(false),

		/**
		 * The default size of the {@link MultiArrayMap} used to store paxos
		 * instances.
		 */
		PINSTANCES_CAPACITY(2000000), // 2M
		/**
		 * The waiting period for paxos instance corpses in order to prevent
		 * inadvertant rebirths because of the missed birthing paxos instance
		 * creation mechanism.
		 */
		MORGUE_DELAY(30000),
		/**
		 * Whether the hibernate option is enabled.
		 */
		HIBERNATE_OPTION(false),
		/**
		 * Whether the pause option is enabled.
		 */
		PAUSE_OPTION(true),
		/**
		 * The time after which the deactivation thread will attempt to pause
		 * idle paxos instances by making a pass over all currently unpaused
		 * instances. This is also the period for which a paxos instance must be
		 * idle in order to be paused.
		 */
		DEACTIVATION_PERIOD(60000), // 30s default

		/**
		 * Refer to documentation in {@link AbstractReconfiguratorDB}.
		 */
		MAX_FINAL_STATE_AGE(3600 * 1000),
		/**
		 * Whether request batching is enabled.
		 */
		BATCHING_ENABLED(true),

		/**
		 * Wait period for forcibly killing a lower paxos instance version in
		 * order to start a higher version.
		 * <p>
		 * FIXME: Unclear what a good default is for good liveness. It doesn't
		 * really matter for safety of reconfiguration.
		 */
		CAN_CREATE_TIMEOUT(5000),
		/**
		 * Wait period before going forth with a missed birthing paxos instance
		 * creation to see if the instance gets normally created anyway.
		 */
		WAIT_TO_GET_CREATED_TIMEOUT(2000),

		/**
		 * The replica receiving the request will simply send the request to the
		 * local application replica, i.e., this essentially disables all paxos
		 * coordination. This is used only for testing.
		 */
		EMULATE_UNREPLICATED(false), ;

		final Object defaultValue;

		PC(Object defaultValue) {
			this.defaultValue = defaultValue;
		}

		@Override
		public Object getDefaultValue() {
			return this.defaultValue;
		}
	}
}

/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.codeexamples.extensions.freight;

import com.graphhopper.jsprit.analysis.toolbox.StopWatch;
import com.graphhopper.jsprit.core.algorithm.VehicleRoutingAlgorithm;
import com.graphhopper.jsprit.core.algorithm.listener.VehicleRoutingAlgorithmListeners;
import org.matsim.application.MATSimAppCommand;
import org.matsim.freight.carriers.controler.CarrierScoringFunctionFactory;
import com.graphhopper.jsprit.core.problem.VehicleRoutingProblem;
import com.graphhopper.jsprit.core.problem.solution.VehicleRoutingProblemSolution;
import com.graphhopper.jsprit.core.util.Solutions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.controler.AbstractModule;
import org.matsim.freight.carriers.*;
import org.matsim.freight.carriers.controler.CarrierModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.config.groups.PlansConfigGroup;
import org.matsim.freight.carriers.jsprit.MatsimJspritFactory;
import org.matsim.freight.carriers.jsprit.NetworkBasedTransportCosts;
import org.matsim.freight.carriers.jsprit.NetworkRouter;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import picocli.CommandLine;

import javax.management.InvalidAttributeValueException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;


/**
 * @see org.matsim.freight.carriers
 */
public class RunFreightExample implements MATSimAppCommand {


	static final Logger log = LogManager.getLogger(RunFreightExample.class);

	private static int nuOfJspritIteration;

	@CommandLine.Option(names = "--carrierFileLocation", description = "Path to the carrierFile.", required = true)
	private static String carrierFilePath;

	@CommandLine.Option(names = "--vehicleTypesFileLocation", description = "Path to the carrierFile.", required = true)
	private static String vehicleTypesFilePath;

	@CommandLine.Option(names = "--JspritIterations", description = "Set the number of jsprit iterations.", required = true)
	private static int JspritIterations;

	@CommandLine.Option(names = "--outputLocationFolder", description = "Path to output folder", required = true)
	private static String outputLocationFolder;

	@CommandLine.Option(names = "--algorithmFile", description = "Path to algorithm file", defaultValue = "")
	private String algorithmFile;

	@CommandLine.Option(names = "--networkChangeEventsFile", description = "Path to events file", defaultValue = "")
	private static String networkChangeEvents;

	public static void main(String[] args) throws ExecutionException, InterruptedException{
		//run(args, false);
		System.exit(new CommandLine(new RunFreightExample()).execute(args));
	}
	//public static void run( String[] args, boolean runWithOTFVis ) throws ExecutionException, InterruptedException{
	public Integer call() throws IOException, InvalidAttributeValueException, ExecutionException, InterruptedException {

/*		for (String arg : args) {
			log.info( arg );
		}

		if ( args.length==0 ) {
			String inputPath = "./scenarios/Berlin/Input/";
			args = new String[] {
					inputPath+"output_carriersNoPlans.xml",
					inputPath + "DHL_vehicleTypes.xml",
					"",														//algorithm
					"1",                                                    //jSprit iterations
					"",														//network change events
					"./scenarios/Berlin/Output/"
			};
		}*/


		// ### config stuff: ###
		Config config = prepareConfig() ;
		log.info("Config prepared");

		// load scenario (this is not loading the freight material):
		//Scenario scenario = ScenarioUtils.loadScenario( config );
		Scenario scenario = prepareScenario( config ) ;

		Controler controler = prepareControler( scenario ) ;

		// Solving the VRP (generate carrier's tour plans)
		runJsprit(controler);

		// ## Start of the MATSim-Run: ##
		//The VSP default settings are designed for person transport simulation. After talking to Kai, they will be set to WARN here. Kai MT may'23
		controler.getConfig().vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);
		controler.run();

		return 0;
		}

	private static Scenario prepareScenario(Config config) {
		Scenario scenario = ScenarioUtils.loadScenario(config);

		CarriersUtils.loadCarriersAccordingToFreightConfig(scenario);

		return scenario;
	}

	private static Config prepareConfig() {

		String carriersFileLocation = carrierFilePath;
		String vehicleTypesFileLocation = vehicleTypesFilePath;
		nuOfJspritIteration = JspritIterations;
		String networkChangeEventsFileLocation = networkChangeEvents;
		String outputLocation = outputLocationFolder;

		Config config = ConfigUtils.createConfig();
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.global().setCoordinateSystem("EPSG:25832");
		config.global().setRandomSeed(4177);
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(outputLocation);

		config.network().setInputFile("https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v6.3/input/berlin-v6.3-network.xml.gz");
		config.network().setInputCRS("EPSG:25832");

		if (!Objects.equals(networkChangeEventsFileLocation, "")){
			log.info("Setting networkChangeEventsInput file: " + networkChangeEventsFileLocation);
			config.network().setTimeVariantNetwork(true);
			config.network().setChangeEventsInputFile(networkChangeEventsFileLocation);
		}
		config.plans().setInputCRS("EPSG:25832");
		config.plans().setActivityDurationInterpretation(PlansConfigGroup.ActivityDurationInterpretation.tryEndTimeThenDuration );
		//freight configstuff
		FreightCarriersConfigGroup freightCarriersConfigGroup = ConfigUtils.addOrGetModule(config, FreightCarriersConfigGroup.class);
		freightCarriersConfigGroup.setCarriersFile(carriersFileLocation);
		freightCarriersConfigGroup.setCarriersVehicleTypesFile(vehicleTypesFileLocation);
		freightCarriersConfigGroup.setTravelTimeSliceWidth(1800);
		freightCarriersConfigGroup.setTimeWindowHandling(FreightCarriersConfigGroup.TimeWindowHandling.enforceBeginnings);



		return config;
	}

	private static Controler prepareControler(Scenario scenario) {
		Controler controller = new Controler(scenario);

		controller.addOverridingModule(new CarrierModule());

		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(CarrierScoringFunctionFactory.class).toInstance(new CarrierScoringFunctionFactory_KeepScore());
			}
		});

		return controller;

	}

	private static void runJsprit(Controler controller) throws ExecutionException, InterruptedException {
		NetworkBasedTransportCosts.Builder netBuilder = NetworkBasedTransportCosts.Builder.newInstance(
				controller.getScenario().getNetwork(), CarriersUtils.getCarrierVehicleTypes(controller.getScenario()).getVehicleTypes().values() );
		final NetworkBasedTransportCosts netBasedCosts = netBuilder.build() ;

		Carriers carriers = CarriersUtils.getCarriers(controller.getScenario());

		HashMap<Id<Carrier>, Integer> carrierActivityCounterMap = new HashMap<>();

		// Fill carrierActivityCounterMap -> basis for sorting the carriers by number of activities before solving in parallel
		for (Carrier carrier : carriers.getCarriers().values()) {
			carrierActivityCounterMap.put(carrier.getId(), carrierActivityCounterMap.getOrDefault(carrier.getId(), 0) + carrier.getServices().size());
			carrierActivityCounterMap.put(carrier.getId(), carrierActivityCounterMap.getOrDefault(carrier.getId(), 0) + carrier.getShipments().size());
		}

		HashMap<Id<Carrier>, Integer> sortedMap = carrierActivityCounterMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2, LinkedHashMap::new));

		ArrayList<Id<Carrier>> tempList = new ArrayList<>(sortedMap.keySet());
		ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
		forkJoinPool.submit(() -> tempList.parallelStream().forEach(carrierId -> {
			Carrier carrier = carriers.getCarriers().get(carrierId);

			double start = System.currentTimeMillis();
			int serviceCount = carrier.getServices().size();
			log.info("start tour planning for " + carrier.getId() + " which has " + serviceCount + " services");

//       for (Carrier carrier : carriers.getCarriers().values()){
			//Carrier carrier = carriers.getCarriers().get(Id.create("kaiser_VERBRAUCHERMARKT_FRISCHE", Carrier.class)); //only for tests

			//currently with try/catch, because CarriersUtils.getJspritIterations will throw an exception if value is not present. Will fix it on MATSim.
			//TODO maybe a future CarriersUtils functionality: Overwrite/set all nuOfJspritIterations. maybe depending on enum (overwriteAll, setNotExisiting, none) ?, KMT Nov2019
			try {
				if(CarriersUtils.getJspritIterations(carrier) <= 0){
					log.warn("Received negative number of jsprit iterations. This is invalid -> Setting number of jsprit iterations for carrier: " + carrier.getId() + " to " + nuOfJspritIteration);
					CarriersUtils.setJspritIterations(carrier, nuOfJspritIteration);
				} else {
					log.warn("Overwriting the number of jsprit iterations for carrier: " + carrier.getId() + ". Value was before " +CarriersUtils.getJspritIterations(carrier) + "and is now " + nuOfJspritIteration);
					CarriersUtils.setJspritIterations(carrier, nuOfJspritIteration);
				}
			} catch (Exception e) {
				log.warn("Setting (missing) number of jsprit iterations for carrier: " + carrier.getId() + " to " + nuOfJspritIteration);
				CarriersUtils.setJspritIterations(carrier, nuOfJspritIteration);
			}

			VehicleRoutingProblem vrp = MatsimJspritFactory.createRoutingProblemBuilder(carrier, controller.getScenario().getNetwork())
					.setRoutingCost(netBasedCosts)
					.build();

			log.warn("Ignore the algorithms file for jsprit and use an algorithm out of the box.");
			Scenario scenario = controller.getScenario();
			FreightCarriersConfigGroup freightConfigGroup = ConfigUtils.addOrGetModule(controller.getConfig(), FreightCarriersConfigGroup.class);
			VehicleRoutingAlgorithm vra = MatsimJspritFactory.loadOrCreateVehicleRoutingAlgorithm(scenario, freightConfigGroup, netBasedCosts, vrp);
			vra.getAlgorithmListeners().addListener(new StopWatch(), VehicleRoutingAlgorithmListeners.Priority.HIGH);
			vra.setMaxIterations(CarriersUtils.getJspritIterations(carrier));
			VehicleRoutingProblemSolution solution = Solutions.bestOf(vra.searchSolutions());

			log.info("tour planning for carrier " + carrier.getId() + " took "
					+ (System.currentTimeMillis() - start) / 1000 + " seconds.");

			CarrierPlan newPlan = MatsimJspritFactory.createPlan(carrier, solution) ;

			log.info("routing plan for carrier " + carrier.getId());
			NetworkRouter.routePlan(newPlan,netBasedCosts) ;
			log.info("routing for carrier " + carrier.getId() + " finished. Tour planning plus routing took "
					+ (System.currentTimeMillis() - start) / 1000 + " seconds.");

			carrier.setSelectedPlan(newPlan) ;
		})).get();
	}


	private static class CarrierScoringFunctionFactory_KeepScore implements CarrierScoringFunctionFactory {
		@Override public ScoringFunction createScoringFunction(Carrier carrier ){
			return new ScoringFunction(){
				@Override public void handleActivity( Activity activity ){
				}
				@Override public void handleLeg( Leg leg ){
				}
				@Override public void agentStuck( double time ){
				}
				@Override public void addMoney( double amount ){
				}
				@Override public void addScore( double amount ){
				}
				@Override public void finish(){
				}
				@Override public double getScore(){
					return CarriersUtils.getJspritScore(carrier.getSelectedPlan()); //2nd Quickfix: Keep the current score -> which ist normally the score from jsprit. -> Better safe JspritScore as own value.
//					return Double.MIN_VALUE; // 1st Quickfix, to have a "double" value for xsd (instead of neg.-Infinity).
//					return Double.NEGATIVE_INFINITY; // Default from KN -> causes errors with reading in carrierFile because Java writes "Infinity", while XSD needs "INF"
				}

				@Override
				public void handleEvent(org.matsim.api.core.v01.events.Event event) {

				}

			};
		}
	}


}
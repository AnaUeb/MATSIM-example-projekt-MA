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

import org.matsim.application.MATSimAppCommand;
import org.matsim.freight.carriers.analysis.RunFreightAnalysisEventBased;
import org.matsim.freight.carriers.controler.CarrierScoringFunctionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import picocli.CommandLine;

import java.io.IOException;
import java.util.concurrent.ExecutionException;


/**
 * @see org.matsim.freight.carriers
 */
public class RunFreightExample implements MATSimAppCommand {


	static final Logger log = LogManager.getLogger(RunFreightExample.class);

	@CommandLine.Option(names = "--carrierFileLocation", description = "Path to the carrierFile.", required = true)
	private static String carrierFilePath;

	@CommandLine.Option(names = "--vehicleTypesFileLocation", description = "Path to the carrierFile.", required = true)
	private static String vehicleTypesFilePath;

	@CommandLine.Option(names = "--jspritIterations", description = "Set the number of jsprit iterations.", required = true)
	private static int jspritIterations;

	@CommandLine.Option(names = "--outputLocationFolder", description = "Path to output folder", required = true)
	private static String outputLocationFolder;

	@CommandLine.Option(names = "--networkFileLocation", description = "Path to network file", required = true)
	private static String networkFile;

	@CommandLine.Option(names = "--networkCRS", description = "CRS of the input network (e.g.\"EPSG:31468\")")
	private static String networkCRS;

	public static void main(String[] args) {
		System.exit(new CommandLine(new RunFreightExample()).execute(args));
	}

	public Integer call() throws ExecutionException, InterruptedException {

		// ### config stuff: ###
		Config config = prepareConfig() ;
		log.info("Config prepared");

		// load scenario (this is not loading the freight material):
		Scenario scenario = ScenarioUtils.loadScenario(config);
		CarriersUtils.loadCarriersAccordingToFreightConfig(scenario);

		//Reset nuOfJspritIterations for all carriers
		for (Carrier carrier : CarriersUtils.getCarriers(scenario).getCarriers().values()) {
			log.warn("Overwriting the number of jsprit iterations for carrier: {}, new value is: {}.", carrier.getId() , jspritIterations);
			CarriersUtils.setJspritIterations(carrier, jspritIterations);
		}

		// Solving the VRP (generate carrier's tour plans)
		CarriersUtils.runJsprit(scenario);


		//prepare controller
		Controler controler = prepareControler( scenario ) ;

		// ## Start of the MATSim-Run: ##
		//Frachtsimulation: Nach Absprache mit KMT auf ignore gesetzt.
		controler.getConfig().vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.ignore);
		controler.run();

		log.info(" Done.");

		//start analysis
		log.info("Start of analysis...");
		var analysis = new RunFreightAnalysisEventBased(
				config.controller().getOutputDirectory().toString()+"/",
				config.controller().getOutputDirectory().toString()+"/analysis", "EPSG:25832");
		try {
			analysis.runAnalysis();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}



		return 0;
		}

	private static Config prepareConfig() {

		String crs = networkCRS;

        Config config = ConfigUtils.createConfig();
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.global().setCoordinateSystem(crs);
		config.global().setRandomSeed(4177);
		config.controller().setLastIteration(0);
		config.controller().setOutputDirectory(outputLocationFolder);

		config.network().setInputFile(networkFile);
		config.network().setInputCRS(crs);

		config.plans().setInputCRS(crs);
		config.plans().setActivityDurationInterpretation(PlansConfigGroup.ActivityDurationInterpretation.tryEndTimeThenDuration );
		//freight configstuff
		FreightCarriersConfigGroup freightCarriersConfigGroup = ConfigUtils.addOrGetModule(config, FreightCarriersConfigGroup.class);
		freightCarriersConfigGroup.setCarriersFile(carrierFilePath);
		freightCarriersConfigGroup.setCarriersVehicleTypesFile(vehicleTypesFilePath);
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
				bind(CarrierScoringFunctionFactory.class).toInstance(new CarrierScoringFunctionFactory_KeepJspritScore());
			}
		});

		return controller;
	}



	private static class CarrierScoringFunctionFactory_KeepJspritScore implements CarrierScoringFunctionFactory {
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
				}

				@Override
				public void handleEvent(org.matsim.api.core.v01.events.Event event) {

				}

			};
		}
	}



}
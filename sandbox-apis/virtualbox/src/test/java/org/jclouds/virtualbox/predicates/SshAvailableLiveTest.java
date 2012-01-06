package org.jclouds.virtualbox.predicates;

import static org.jclouds.virtualbox.domain.ExecutionType.HEADLESS;
import static org.jclouds.virtualbox.experiment.TestUtils.computeServiceForLocalhostAndGuest;
import static org.jclouds.virtualbox.util.MachineUtils.applyForMachine;
import static org.jclouds.virtualbox.util.MachineUtils.lockSessionOnMachineAndApply;
import static org.testng.Assert.assertTrue;
import static org.virtualbox_4_1.LockType.Shared;

import java.util.concurrent.TimeUnit;

import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.domain.Credentials;
import org.jclouds.net.IPSocket;
import org.jclouds.predicates.InetSocketAddressConnect;
import org.jclouds.predicates.RetryablePredicate;
import org.jclouds.virtualbox.BaseVirtualBoxClientLiveTest;
import org.jclouds.virtualbox.domain.ExecutionType;
import org.jclouds.virtualbox.domain.HardDisk;
import org.jclouds.virtualbox.domain.StorageController;
import org.jclouds.virtualbox.domain.VmSpec;
import org.jclouds.virtualbox.functions.CreateAndInstallVm;
import org.jclouds.virtualbox.functions.LaunchMachineIfNotAlreadyRunning;
import org.testng.annotations.Test;
import org.virtualbox_4_1.IMachine;
import org.virtualbox_4_1.IProgress;
import org.virtualbox_4_1.ISession;
import org.virtualbox_4_1.StorageBus;
import org.virtualbox_4_1.VirtualBoxManager;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

@Test(groups = "live", singleThreaded = true, testName = "SshAvailableLiveTest")
public class SshAvailableLiveTest extends BaseVirtualBoxClientLiveTest {

   private String guestId = "guest";
   private String hostId = "host";

   private String vmName = "jclouds-image-virtualbox-iso-to-machine-sshtest";

   @Test
   public void testSshDaemonIsRunning() {
      VirtualBoxManager manager = (VirtualBoxManager) context.getProviderSpecificContext().getApi();
      ComputeServiceContext localHostContext = computeServiceForLocalhostAndGuest(
              hostId, "localhost", guestId, "localhost", new Credentials("toor", "password"));

      getNodeWithSshDaemonRunning(manager, localHostContext);
      ensureMachineIsLaunched(vmName);
      RetryablePredicate<String> predicate = new RetryablePredicate<String>(
              new SshAvailable(localHostContext), 5, 1,
              TimeUnit.SECONDS);
      assertTrue(predicate.apply(guestId));

      lockSessionOnMachineAndApply(manager, Shared, vmName, new Function<ISession, Void>() {

         @Override
         public Void apply(ISession session) {
            IProgress powerDownProgress = session.getConsole().powerDown();
            powerDownProgress.waitForCompletion(-1);
            return null;
         }

      });
   }

   private IMachine getNodeWithSshDaemonRunning(VirtualBoxManager manager, ComputeServiceContext localHostContext) {
      try {
         Predicate<IPSocket> socketTester = new RetryablePredicate<IPSocket>(
                 new InetSocketAddressConnect(), 10, 1, TimeUnit.SECONDS);
         String vmId = "jclouds-image-iso-2";

         StorageController ideController = StorageController.builder().name("IDE Controller").bus(StorageBus.IDE)
                 .attachISO(0, 0, operatingSystemIso)
                 .attachHardDisk(HardDisk.builder().diskpath(adminDisk).controllerPort(0).deviceSlot(1).build()).build();
         VmSpec vmSpecification = VmSpec.builder().id(vmId).name(vmName).osTypeId("")
         		  .memoryMB(512)
                 .controller(ideController)
                 .forceOverwrite(true).build();

         return new CreateAndInstallVm(manager, guestId, localHostContext,
                 hostId, socketTester, "127.0.0.1", 8080, HEADLESS).apply(vmSpecification);
      } catch (IllegalStateException e) {
         // already created
         return manager.getVBox().findMachine(vmName);
      }
   }

   private void ensureMachineIsLaunched(String vmName) {
      applyForMachine(manager, vmName, new LaunchMachineIfNotAlreadyRunning(
              manager, ExecutionType.GUI, ""));
   }

}
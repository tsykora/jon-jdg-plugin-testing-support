package net.dataforte.infinispan.web;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.context.Flag;
import org.infinispan.interceptors.InvocationContextInterceptor;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.TransactionTable;
import org.infinispan.transaction.lookup.TransactionManagerLookup;
import org.infinispan.transaction.tm.DummyTransaction;
import org.infinispan.transaction.tm.DummyTransactionManager;
import org.infinispan.transaction.xa.recovery.RecoveryAdminOperations;
import org.infinispan.tx.recovery.RecoveryTestUtil;
import org.infinispan.tx.recovery.admin.InDoubtWithCommitFailsTest;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Infinispan configuration wizard
 *
 * @author tsykora
 */
@WebListener()
public class Listener implements ServletContextListener {
   private static final String CONTAINER = "container";
   private static final String CONTAINER2 = "container2";
   private static final String CONTAINER3 = "container3";
   private static final String CACHE = "cache";
   EmbeddedCacheManager manager;

   EmbeddedCacheManager managerNycSite;
   EmbeddedCacheManager managerLonSite;

//   EmbeddedCacheManager managerForHrServer;
//   EmbeddedCacheManager managerForHrTargetServer;

   @Override
   public void contextInitialized(ServletContextEvent sce) {

//      GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
//      global.transport().defaultTransport();
//      global.globalJmxStatistics().enable();

      // for xsite
      GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
      global.transport().defaultTransport();
      global.globalJmxStatistics().enable();

      // transactions with recovery management
      ConfigurationBuilder configTrans = new ConfigurationBuilder();
             configTrans.jmxStatistics().enable();
             configTrans.transaction().transactionManagerLookup(new org.infinispan.tx.recovery.RecoveryDummyTransactionManagerLookup()).
                   transactionMode(TransactionMode.TRANSACTIONAL).lockingMode(LockingMode.OPTIMISTIC);
             configTrans.transaction().useSynchronization(false);
      configTrans.transaction().recovery().enable();
      configTrans.locking().useLockStriping(false);
      configTrans.clustering().cacheMode(CacheMode.DIST_SYNC);
      configTrans.deadlockDetection().enable();

      // for default cache
      ConfigurationBuilder configJmxOnly = new ConfigurationBuilder();
      configJmxOnly.jmxStatistics();

      // FCS + Dist
      ConfigurationBuilder configFCSdist = new ConfigurationBuilder();
      configFCSdist.jmxStatistics().enable();
      configFCSdist.loaders().passivation(true).addFileCacheStore().location("/tmp/");
      configFCSdist.clustering().cacheMode(CacheMode.DIST_ASYNC).l1().enable();

      // Invalidation
      ConfigurationBuilder configInvalidation = new ConfigurationBuilder();
      configInvalidation.jmxStatistics().enable();
      configInvalidation.clustering().cacheMode(CacheMode.INVALIDATION_ASYNC);

//      ConfigurationBuilder configRollUps = new ConfigurationBuilder();
//      configRollUps.jmxStatistics().enable();
//      configRollUps.loaders().addStore;

      manager = new DefaultCacheManager(global.build(), configJmxOnly.build());

//      try {
//         managerQuery = new DefaultCacheManager("dynamic-indexing-distribution.xml");
//      } catch (IOException e) {
//         e.printStackTrace();
//      }

      final Properties properties = new Properties();
      properties.put("default.directory_provider", "ram");
      properties.put("lucene_version", "LUCENE_CURRENT");

      ConfigurationBuilder configQueryIndexer = new ConfigurationBuilder();
      configQueryIndexer.jmxStatistics().enable();
      configQueryIndexer.locking().useLockStriping(false);
      configQueryIndexer.clustering().cacheMode(CacheMode.DIST_SYNC);
      configQueryIndexer.indexing().enable().indexLocalOnly(true).withProperties(properties);


      try {
         managerLonSite = new DefaultCacheManager("xsite-test-lon.xml");
      } catch (IOException e) {
         System.out.println("********** PROBLEM while parsing xsite-test-lon.xml *************");
         e.printStackTrace();
      }

      // this is LON and is backed up to NYC
      try {
         managerNycSite = new DefaultCacheManager("xsite-test-nyc.xml");
      } catch (IOException e) {
         System.out.println("********** PROBLEM xsite-test-nyc.xml *************");
         e.printStackTrace();
      }

      // a way how to access protocol in Transport
      // manager.getTransport().getChannel().getProtocolStack().addProtocol();

      manager.defineConfiguration("transactionalCache", configTrans.build());
      manager.defineConfiguration("fcsDistCache", configFCSdist.build());
      manager.defineConfiguration("default", configJmxOnly.build());
      manager.defineConfiguration("invalidationCache", configInvalidation.build());
      manager.defineConfiguration("___default", configQueryIndexer.build());

      // some initial puts, these puts will cause:
      // INFO  [org.infinispan.jmx.CacheJmxRegistration] (MSC service thread 1-7) ISPN000031:
      // MBeans were successfully registered to the platform MBean server.

      // after that jon agent can recognize that and commit into JON view
      System.out.println("Putting entries into caches to register components into platform MBean server..... ");

      Cache c = manager.getCache("default");
      c.put("key1", "value1");

      Cache ctrans = manager.getCache("transactionalCache");
      Cache cfcs = manager.getCache("fcsDistCache");
      Cache cinval = manager.getCache("invalidationCache");

      ctrans.put("key1", "value1");
      cfcs.put("key1", "value1");
      cinval.put("key1", "value1");

      // XSite stuff
      // put into LON -- should be backed up (replicated) to NYC
      Cache clon = managerLonSite.getCache("LonCache");
      clon.put("keyLon1", "valueLon1");
      clon.put("keyLon2", "valueLon2");
      // put into NYC
      Cache cnyc = managerNycSite.getCache("NycCacheBackupForLon");
      cnyc.put("keyNyc1", "valueNyc1"); // one simple put

      Cache queryCache = manager.getCache("___default");
      queryCache.put("keyQuery1", "valueQuery1");
      queryCache.getAdvancedCache().withFlags(Flag.SKIP_INDEXING).put("notIndexedKey1", "notIndexedValue1");

      // <editor-fold name=transactions>
      // **********************
      // In doubt transactions preparation stuff

      RecoveryAdminOperations rao = ctrans.getAdvancedCache().getComponentRegistry().getComponent(RecoveryAdminOperations.class);
      try {
         testInDoubt(true, ctrans, rao);
      } catch (XAException e) {
         System.out.println("\nEXCEPTION IN TEST DOUBT METHOD.... Listener.java\n");
         e.printStackTrace();
      }
//    </editor-fold name=transactions>

      // <editor-fold name=RollUps>


// **********************
// Rolling upgrades stuff
// NOTE: Rolling upgrades may be still broken for ISPN Server because of changes in HotRod
//
//      System.out.println("\n\n\n ROLLING UPGRADES SECTION ........... \n\n\n");
//
//
//      GlobalConfigurationBuilder globalHr = GlobalConfigurationBuilder.defaultClusteredBuilder();
//      globalHr.transport().defaultTransport();
//      globalHr.globalJmxStatistics().jmxDomain("org.infinispan.hr").enable();
//
//
//      ConfigurationBuilder configSourceCluster = new ConfigurationBuilder();
//      configSourceCluster.jmxStatistics().enable().jmxStatistics();
//
//
//      managerForHrServer = new DefaultCacheManager(globalHr.build(), configSourceCluster.build());
//
//      // HOT ROD SERVER -- need 2 for Rolling Upgrades
//      HotRodTestingUtil.startHotRodServer(managerForHrServer, 11222); // 12311
//
//      Cache hrCache = managerForHrServer.getCache("defaultRollUps");
//      hrCache.put("HR_key1", "HR_value1");
//      hrCache.put("HR_key2", "HR_value2");
//      hrCache.put("HR_key3", "HR_value3");
//
//
//      System.out.println("\n\n\n" + managerForHrServer.getCacheNames().toString() + "\n\n\n");
//
//
//      RemoteCacheStoreConfig remoteCacheStoreConfig = new RemoteCacheStoreConfig();
//
//      remoteCacheStoreConfig.setRemoteCacheName("defaultRollUps");
//      Properties properties = new Properties();
//      properties.put("infinispan.client.hotrod.server_list", "localhost:" + 11222);
//      remoteCacheStoreConfig.setHotRodClientProperties(properties);
//      remoteCacheStoreConfig.setRawValues(true);
//      remoteCacheStoreConfig.purgeOnStartup(false);
//
//
//      // TODO OTHER CONFIG?
//
//      // and put it into targetCluster to point source cluster
//
//      RemoteCacheStore remoteCacheStore = new RemoteCacheStore();
//      try {
//         // Do we need Marshaller?
//         remoteCacheStore.init(remoteCacheStoreConfig, hrCache, null);
//         remoteCacheStore.start();
//         // **********************************************************
//      } catch (CacheLoaderException e) {
//         System.out.println("\n\n\n\n\n PROBLEM WITH REMOTE CACHE STORE \n\n\n");
//         e.printStackTrace();
//      }
//
//      System.out.println("\n\n\n\n\nREMOTE CACHE STORE STATISTICS --- before put into RCS");
//      for (String stat : remoteCacheStore.getRemoteCache().stats().getStatsMap().keySet()) {
//         System.out.println("Statistic: " + stat + " ---> value: " + remoteCacheStore.getRemoteCache().stats().getStatsMap().get(stat));
//      }
//
//      System.out.println("\n\n\n\n\nREMOTE CACHE STORE STATISTICS --- after put 2 entries into RCS");
//      remoteCacheStore.getRemoteCache().put("putIntoRCS1key", "putIntoRCS1value");
//      remoteCacheStore.getRemoteCache().put("putIntoRCS2key", "putIntoRCS2value");
//
//      System.out.println("\n\n\n\n\nREMOTE CACHE STORE STATISTICS --- before put into RCS");
//      for (String stat : remoteCacheStore.getRemoteCache().stats().getStatsMap().keySet()) {
//         System.out.println("Statistic: " + stat + " ---> value: " + remoteCacheStore.getRemoteCache().stats().getStatsMap().get(stat));
//      }
//
//
      // source cluster is remote cache store for me
      // this remoteCacheStore is SOURCE cache...
      // I can see it now using this
//
//      GlobalConfigurationBuilder globalTargetHr = GlobalConfigurationBuilder.defaultClusteredBuilder();
//      globalTargetHr.transport().defaultTransport();
//      globalTargetHr.globalJmxStatistics().jmxDomain("org.infinispan.target.hr").enable();
//
//      ConfigurationBuilder configTargetCluster = new ConfigurationBuilder();
//      configTargetCluster.jmxStatistics().enable().jmxStatistics();
//
//      configTargetCluster.loaders().addStore(RemoteCacheStoreConfigurationBuilder.class)
//            .fetchPersistentState(false)
//            .ignoreModifications(false)
//            .purgeOnStartup(false)
//            .remoteCacheName("defaultRollUps")
//                  // Set Upd rawValues and Wrapping and maybe entryWrapper properly after fixes in Rolling Upgrades
////            .rawValues(true)
////            .hotRodWrapping(true)
////            .entryWrapper(??)
//            .addServer()
//            .host("127.0.0.1").port(11222);
//
//      managerForHrTargetServer = new DefaultCacheManager(globalTargetHr.build(), configTargetCluster.build());
//
//      // HOT ROD SERVER -- need 2 for Rolling Upgrades
//      HotRodTestingUtil.startHotRodServer(managerForHrTargetServer, 11322); // 12311
//
//      Cache hrTargetCache = managerForHrTargetServer.getCache("defaultRollUps");
//
//      System.out.println("\n\n\n\nEntries in HR SOURCE CACHE before put to target: " + hrCache.getAdvancedCache().getStats().getTotalNumberOfEntries() +
//                               "\n\n");
//
//      hrTargetCache.put("putIntoTargetCache1key", "putIntoTargetCache1value");
//
//      System.out.println("\n\n\n\nEntries in HR TARGET CACHE: " + hrTargetCache.getAdvancedCache().getStats().getTotalNumberOfEntries() +
//                               "\n\n");
//      System.out.println("\n\n\n\nEntries in HR SOURCE CACHE after put to target: " + hrCache.getAdvancedCache().getStats().getTotalNumberOfEntries() +
//                               "\n\n");
//
//
//      System.out.println("\n\n\n\n\nREMOTE CACHE STORE STATISTICS --- after put 1 into target server");
//      for (String stat : remoteCacheStore.getRemoteCache().stats().getStatsMap().keySet()) {
//         System.out.println("Statistic: " + stat + " ---> value: " + remoteCacheStore.getRemoteCache().stats().getStatsMap().get(stat));
//      }
      // </editor-fold>

      sce.getServletContext().setAttribute(CONTAINER, manager.toString());
      sce.getServletContext().setAttribute(CONTAINER2, managerLonSite.toString());
      sce.getServletContext().setAttribute(CONTAINER3, managerNycSite.toString());

      sce.getServletContext().setAttribute("manager", manager);
      sce.getServletContext().setAttribute("managerRemoteLon", managerLonSite);
      sce.getServletContext().setAttribute("managerRemoteNyc", managerNycSite);
   }


   private void testInDoubt(boolean commit, Cache ctrans, RecoveryAdminOperations recoveryAdminOperations) throws XAException {

      assert recoveryAdminOperations.showInDoubtTransactions().isEmpty();
      TransactionTable tt0 = ctrans.getAdvancedCache().getComponentRegistry().getComponent(TransactionTable.class);


      // need to intercept failure during commands
      ctrans.getAdvancedCache().addInterceptorBefore(new InDoubtWithCommitFailsTest.ForceFailureInterceptor(),
                                                     InvocationContextInterceptor.class);


      DummyTransaction dummyTransaction1 = RecoveryTestUtil.beginAndSuspendTx(ctrans, "key1");
      DummyTransaction dummyTransaction2 = RecoveryTestUtil.beginAndSuspendTx(ctrans, "key2");
      DummyTransaction dummyTransaction3 = RecoveryTestUtil.beginAndSuspendTx(ctrans, "key3");
      RecoveryTestUtil.prepareTransaction(dummyTransaction1);
      RecoveryTestUtil.prepareTransaction(dummyTransaction2);
      RecoveryTestUtil.prepareTransaction(dummyTransaction3);

      List<DummyTransaction> transactions = new LinkedList<DummyTransaction>();
      transactions.add(dummyTransaction1);
      transactions.add(dummyTransaction2);
      transactions.add(dummyTransaction3);

      assert tt0.getLocalTxCount() == 3;

      for (DummyTransaction dTrans : transactions) {
         try {
            // will fail because of InDoubtWithCommitFailsTest.ForceFailureInterceptor()
            // remove this interceptor later to be able to force manually commit/rollback/forget via JMX (RHQ/jconsole)
            if (commit) {
               RecoveryTestUtil.commitTransaction(dTrans);
            } else {
               RecoveryTestUtil.rollbackTransaction(dTrans);
            }
            assert false : "exception expected";
         } catch (Exception e) {
            //expected -- induced failure
         }
      }

      // REMOVE FAILING INTERCEPTOR from a cache
      System.out.println("\n\n\n Removing INTERCEPTOR causing induced failures during commits & rollbacks... ");
      ctrans.getAdvancedCache().removeInterceptor(InDoubtWithCommitFailsTest.ForceFailureInterceptor.class);
   }

   @Override
   public void contextDestroyed(ServletContextEvent sce) {
      sce.getServletContext().removeAttribute(CACHE);
      sce.getServletContext().removeAttribute(CONTAINER);
      sce.getServletContext().removeAttribute(CONTAINER2);
      sce.getServletContext().removeAttribute(CONTAINER3);

      manager.stop();
      managerLonSite.stop();
      managerNycSite.stop();
//      managerForHrServer.stop();
//      managerForHrTargetServer.stop();
   }
}

class RecoveryDummyTransactionManagerLookup implements TransactionManagerLookup {
   @Override
   public synchronized TransactionManager getTransactionManager() throws Exception {
      DummyTransactionManager dtm = new DummyTransactionManager();
      dtm.setUseXaXid(true);
      return dtm;
   }
}
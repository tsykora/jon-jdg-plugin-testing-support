package net.dataforte.infinispan.web;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.IOException;

/**
 * Infinispan configuration wizard
 *
 * @author tsykora
 *
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

   @Override
   public void contextInitialized(ServletContextEvent sce) {

//      GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
//      global.transport().defaultTransport();
//      global.globalJmxStatistics().enable();

      // for xsite
      GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
//      global.transport().addProperty("configurationFile", "jgroups-LON.xml");
      global.transport().defaultTransport();
      global.globalJmxStatistics().enable();


      ConfigurationBuilder configJmxOnly = new ConfigurationBuilder();
      configJmxOnly.jmxStatistics().enable().indexing().indexLocalOnly(false).enable();

      // deadlock + transactions
      ConfigurationBuilder configTrans = new ConfigurationBuilder();
      configTrans.jmxStatistics().enable();
      configTrans.transaction().transactionManagerLookup(new org.infinispan.transaction.lookup.GenericTransactionManagerLookup()).
            transactionMode(TransactionMode.TRANSACTIONAL).lockingMode(LockingMode.OPTIMISTIC);
      configTrans.deadlockDetection().enable();

      // FCS + Dist
      ConfigurationBuilder configFCSdist = new ConfigurationBuilder();
      configFCSdist.jmxStatistics().enable();
      configFCSdist.loaders().passivation(true).addFileCacheStore().location("/tmp/");
      configFCSdist.clustering().cacheMode(CacheMode.DIST_ASYNC).l1().enable();

      // Invalidation
      ConfigurationBuilder configInvalidation = new ConfigurationBuilder();
      configInvalidation.jmxStatistics().enable();
      configInvalidation.clustering().cacheMode(CacheMode.INVALIDATION_ASYNC);


      //
//      ConfigurationBuilder configRollUps = new ConfigurationBuilder();
//      configRollUps.jmxStatistics().enable();
//      configRollUps.loaders().addStore;


      manager = new DefaultCacheManager(global.build(), configJmxOnly.build());

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

      // HOT ROD SERVER -- need 2 for Rolling Upgrades
//      HotRodTestingUtil.startHotRodServer(managerForHrServer, 15002); // 12311



        // way how to access protocol in Transport
//      manager.getTransport().getChannel().getProtocolStack().addProtocol();

      manager.defineConfiguration("transactionalCache", configTrans.build());
      manager.defineConfiguration("fcsDistCache", configFCSdist.build());
      manager.defineConfiguration("default", configJmxOnly.build());
      manager.defineConfiguration("invalidationCache", configInvalidation.build());


      sce.getServletContext().setAttribute(CONTAINER, manager.toString());
      sce.getServletContext().setAttribute(CONTAINER2, managerLonSite.toString());
      sce.getServletContext().setAttribute(CONTAINER3, managerNycSite.toString());
//      sce.getServletContext().setAttribute(CACHE, manager.getCache("default"));
      sce.getServletContext().setAttribute("manager", manager);
      sce.getServletContext().setAttribute("managerRemoteLon", managerLonSite);
      sce.getServletContext().setAttribute("managerRemoteNyc", managerNycSite);



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
   }
}

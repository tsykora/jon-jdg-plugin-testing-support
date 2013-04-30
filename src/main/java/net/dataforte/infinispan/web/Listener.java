package net.dataforte.infinispan.web;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.BackupConfiguration;
import org.infinispan.configuration.cache.BackupFailurePolicy;
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

/**
 * Web application lifecycle listener.
 *
 * @author aerohner
 * @author tsykora
 *
 */
@WebListener()
public class Listener implements ServletContextListener {
   private static final String CONTAINER = "container";
   private static final String CACHE = "cache";
   EmbeddedCacheManager manager;

   @Override
   public void contextInitialized(ServletContextEvent sce) {
//      GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
//      global.transport().defaultTransport();
//      global.globalJmxStatistics().enable();

      // for xsite
      GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
      global.transport().addProperty("configurationFile", "jgroups.xml");
      global.site().localSite("LON");
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


      // xsite config
      ConfigurationBuilder configXsite = new ConfigurationBuilder();
      configXsite.jmxStatistics().enable();
      configXsite.sites().addBackup()
            .site("NYC")
            .backupFailurePolicy(BackupFailurePolicy.WARN)
            .strategy(BackupConfiguration.BackupStrategy.SYNC)
            .replicationTimeout(12000)
            .sites().addInUseBackupSite("NYC")
            .sites().addBackup()
            .site("SFO")
            .backupFailurePolicy(BackupFailurePolicy.IGNORE)
            .strategy(BackupConfiguration.BackupStrategy.ASYNC)
            .sites().addInUseBackupSite("SFO");

      ConfigurationBuilder configUsersWithLON = new ConfigurationBuilder();
      configUsersWithLON.sites().backupFor().remoteCache("users").remoteSite("LON");
      configUsersWithLON.sites().addBackup().site("NYC").backupFailurePolicy(BackupFailurePolicy.WARN).strategy(BackupConfiguration.BackupStrategy.SYNC);
      configUsersWithLON.sites().addBackup().site("SFO").backupFailurePolicy(BackupFailurePolicy.WARN).strategy(BackupConfiguration.BackupStrategy.SYNC);




      manager = new DefaultCacheManager(global.build(), configJmxOnly.build());

      // I need to config it using RELAY2 protocol
//      manager.getTransport().getChannel().getProtocolStack().addProtocol();

      manager.defineConfiguration("transactionalCache", configTrans.build());
      manager.defineConfiguration("fcsDistCache", configFCSdist.build());
      manager.defineConfiguration("default", configJmxOnly.build());
      manager.defineConfiguration("invalidationCache", configInvalidation.build());

      manager.defineConfiguration("xsites", configXsite.build());
      manager.defineConfiguration("users", configUsersWithLON.build());

      sce.getServletContext().setAttribute(CONTAINER, manager.toString());
      sce.getServletContext().setAttribute(CACHE, manager.getCache("default"));
      sce.getServletContext().setAttribute("manager", manager);


      System.out.println("Context initialized.... putting 4 entries into default cache..... ");


      // some initial puts, these puts will cause:
      // INFO  [org.infinispan.jmx.CacheJmxRegistration] (MSC service thread 1-7) ISPN000031:
      // MBeans were successfully registered to the platform MBean server.

      // after that jon agent can recognize that and commit into JON view

      Cache c = manager.getCache("default");
      c.put("key1", "value1");
      c.put("key2", "value2");
      c.put("key3", "value3");
      c.put("key4", "value4");

      System.out.println("Putting entries into other caches..... ");

      Cache ctrans = manager.getCache("transactionalCache");
      Cache cfcs = manager.getCache("fcsDistCache");
//        Cache cxsite = manager.getCache("xsiteCache");
      Cache cinval = manager.getCache("invalidationCache");
      Cache cxsites = manager.getCache("xsites");
      Cache cusers = manager.getCache("users");

      ctrans.put("key1", "value1");
      cfcs.put("key1", "value1");
//        cxsite.put("key1", "value1");
      cinval.put("key1", "value1");

      cxsites.put("key1", "value1");
      cusers.put("key1", "value1");
   }

   @Override
   public void contextDestroyed(ServletContextEvent sce) {
      sce.getServletContext().removeAttribute(CACHE);
      sce.getServletContext().removeAttribute(CONTAINER);

      manager.stop();
   }
}

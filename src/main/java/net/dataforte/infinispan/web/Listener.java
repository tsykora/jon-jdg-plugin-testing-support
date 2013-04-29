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
      GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
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


      // xsite config
//      ConfigurationBuilder configXsite = new ConfigurationBuilder();
//      configXsite.jmxStatistics().enable();
//      configXsite.sites().addBackup()
//            .site("NYC")
//            .backupFailurePolicy(BackupFailurePolicy.WARN)
//            .strategy(BackupConfiguration.BackupStrategy.SYNC)
//            .replicationTimeout(12000)
//            .sites().addInUseBackupSite("NYC")
//            .sites().addBackup()
//            .site("SFO")
//            .backupFailurePolicy(BackupFailurePolicy.IGNORE)
//            .strategy(BackupConfiguration.BackupStrategy.ASYNC)
//            .sites().addInUseBackupSite("SFO");





      manager = new DefaultCacheManager(global.build(), configJmxOnly.build());

      manager.defineConfiguration("transactionalCache", configTrans.build());
      manager.defineConfiguration("fcsDistCache", configFCSdist.build());
      manager.defineConfiguration("default", configJmxOnly.build());
//      manager.defineConfiguration("xsiteCache", configXsite.build());
      manager.defineConfiguration("invalidationCache", configInvalidation.build());

      sce.getServletContext().setAttribute(CONTAINER, manager.toString());
      sce.getServletContext().setAttribute(CACHE, manager.getCache("default"));
      sce.getServletContext().setAttribute("manager", manager);


      System.out.println("Context initialized.... putting 4 entries into default cache..... ");

      Cache c = manager.getCache("default");
      c.put("key1", "value1");
      c.put("key2", "value2");
      c.put("key3", "value3");
      c.put("key4", "value4");
   }

   @Override
   public void contextDestroyed(ServletContextEvent sce) {
      sce.getServletContext().removeAttribute(CACHE);
      sce.getServletContext().removeAttribute(CONTAINER);

      manager.stop();
   }
}

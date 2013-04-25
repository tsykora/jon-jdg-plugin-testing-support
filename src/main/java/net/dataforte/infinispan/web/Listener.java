package net.dataforte.infinispan.web;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

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
      global.globalJmxStatistics().enable();
      ConfigurationBuilder config = new ConfigurationBuilder();
      config.jmxStatistics().enable();

      // xsite config
//      config.sites().addBackup()
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


      config.loaders().addFileCacheStore().location("/tmp/");

      manager = new DefaultCacheManager(global.build(), config.build());
      sce.getServletContext().setAttribute(CONTAINER, manager.toString());
      sce.getServletContext().setAttribute(CACHE, manager.getCache());

      sce.getServletContext().setAttribute("manager", manager);

      System.out.println("Context initialized.... putting 4 entries into default cache..... ");
      manager.getCache().put("key1", "value1");
      manager.getCache().put("key2", "value2");
      manager.getCache().put("key3", "value3");
      manager.getCache().put("key4", "value4");
   }

   @Override
   public void contextDestroyed(ServletContextEvent sce) {
      sce.getServletContext().removeAttribute(CACHE);
      sce.getServletContext().removeAttribute(CONTAINER);
      manager.stop();
   }
}

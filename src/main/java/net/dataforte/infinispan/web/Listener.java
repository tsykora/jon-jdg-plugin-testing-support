package net.dataforte.infinispan.web;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
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
import java.util.LinkedList;
import java.util.List;

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


   @Override
   public void contextInitialized(ServletContextEvent sce) {

      GlobalConfigurationBuilder global = GlobalConfigurationBuilder.defaultClusteredBuilder();
      global.transport().defaultTransport();
      global.globalJmxStatistics().enable().jmxDomain("org.infinispan");

      ConfigurationBuilder configTrans = new ConfigurationBuilder();
      configTrans.jmxStatistics().enable();
      configTrans.transaction().transactionManagerLookup(new org.infinispan.tx.recovery.RecoveryDummyTransactionManagerLookup()).
            transactionMode(TransactionMode.TRANSACTIONAL).lockingMode(LockingMode.OPTIMISTIC);
      configTrans.transaction().useSynchronization(false);
      configTrans.transaction().recovery().enable();
      configTrans.locking().useLockStriping(false);
      configTrans.clustering().cacheMode(CacheMode.DIST_SYNC);
      configTrans.deadlockDetection().enable();

      manager = new DefaultCacheManager(global.build(), configTrans.build());

      Cache ctrans = manager.getCache("ctrans1");

      RecoveryAdminOperations rao = ctrans.getAdvancedCache().getComponentRegistry().getComponent(RecoveryAdminOperations.class);

      System.out.println("Running migrated test right now.... \n\n|\n|\nv\n\n");
      try {
         testInDoubt(true, ctrans, rao);
      } catch (XAException e) {
         System.out.println("\nEXCEPTION IN TEST DOUBT METHOD.... Listener.Java\n");
         e.printStackTrace();
      }

      sce.getServletContext().setAttribute(CONTAINER, manager.toString());
//      sce.getServletContext().setAttribute(CONTAINER2, managerLonSite.toString());
//      sce.getServletContext().setAttribute(CONTAINER3, managerNycSite.toString());

      sce.getServletContext().setAttribute("manager", manager);
//      sce.getServletContext().setAttribute("managerRemoteLon", managerLonSite);
//      sce.getServletContext().setAttribute("managerRemoteNyc", managerNycSite);

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
      System.out.println("\n\n\n Removing failing INTERCEPTOR... ");
      ctrans.getAdvancedCache().removeInterceptor(InDoubtWithCommitFailsTest.ForceFailureInterceptor.class);

   }


   @Override
   public void contextDestroyed(ServletContextEvent sce) {
      sce.getServletContext().removeAttribute(CACHE);
//      sce.getServletContext().removeAttribute(CONTAINER);
//      sce.getServletContext().removeAttribute(CONTAINER2);
//      sce.getServletContext().removeAttribute(CONTAINER3);

      manager.stop();
//      managerLonSite.stop();
//      managerNycSite.stop();
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
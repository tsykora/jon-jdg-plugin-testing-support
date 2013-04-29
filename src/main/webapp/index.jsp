<%@ page import="org.infinispan.Cache" %>
<%@ page import="org.infinispan.Version" %>
<%@ page import="org.infinispan.interceptors.CacheMgmtInterceptor" %>
<%@ page import="org.infinispan.interceptors.base.CommandInterceptor" %>
<%@ page import="org.infinispan.manager.EmbeddedCacheManager" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Random" %>

<% final Random r = new Random(); %>

<html>
<body>

<form action="index.jsp">

    This simple application was developed as a support for testing JON + rhq library / server plugin for JDG. <br/>
    Issue operation in JON and check results immediately by refreshing page. <br/>
    Or issue some operations here and check results in JON after discovery times. <br/><br/>

    Using: <%=Version.printVersion()%><br/>
    CacheManager instance: <%=application.getAttribute("container")%><br/>

    Now operating on: <b><%=application.getAttribute("cache")%><br/></b>

    <%
        EmbeddedCacheManager manager = (EmbeddedCacheManager) application.getAttribute("manager");
        // instantiate all caches
        Cache c = manager.getCache("default");

        Cache ctrans = manager.getCache("transactionalCache");
        Cache cfcs = manager.getCache("fcsDistCache");
//        Cache cxsite = manager.getCache("xsiteCache");
        Cache cinval = manager.getCache("invalidationCache");


        ctrans.put("key1", "value1");
        cfcs.put("key1", "value1");
//        cxsite.put("key1", "value1");
        cinval.put("key1", "value1");


        StringBuilder sb = new StringBuilder();
        for (String name : manager.getCacheNames()) {
            sb.append(name + " ");
        }


        if (request.getParameter("addEntry") != null) {
            int rand = r.nextInt(1000);
            c.put("key" + rand, "value" + rand);
        }

        if (request.getParameter("stopCache") != null) {
            c.stop();
        }

        if (request.getParameter("startCache") != null) {
            c.start();
        }

        if (request.getParameter("clearCache") != null) {
            c.clear();
        }

        if (request.getParameter("resetStatistics") != null) {
            for (CommandInterceptor interceptor : (List<CommandInterceptor>) c.getAdvancedCache().getInterceptorChain()) {
                boolean isSubclass = CacheMgmtInterceptor.class.isAssignableFrom(interceptor.getClass());
                if (isSubclass) {
                    CacheMgmtInterceptor cacheMgmtInterceptor = (CacheMgmtInterceptor) interceptor;
                    cacheMgmtInterceptor.resetStatistics();
                }
            }
        }


        int currEntries = c.getAdvancedCache().getStats().getCurrentNumberOfEntries();
        long totalEntries = c.getAdvancedCache().getStats().getTotalNumberOfEntries();
        String status = c.getStatus().toString();



//        c.getAdvancedCache().get

    %>

    <br/>
    <br/>
    Stats: <br/>
    Current entries:
    <%=currEntries%> <br/>
    Total entries:
    <%=totalEntries%> <br/>
    Cache status:
    <%=status%> <br/> <br/> <br/>


    All caches names excluding ___defaultcache (= started named caches from JON, or by app): <br/> <b><%=sb.toString()%><br/><br/></b>

    <br/>
    <br/>

    <button type="submit" name="addEntry" value="Add Entry Button">Add Entry into Cache</button>
    <button type="submit" name="clearCache" value="Clear Cache Button">CLEAR Cache</button>
    <button type="submit" name="resetStatistics" value="Reset Statistics Button">Reset Cache Statistics</button>


    <br/>
    <br/>
    <button type="submit" name="startCache" value="Start Cache Button">START Cache</button>
    <button type="submit" name="stopCache" value="Stop Cache Button">STOP Cache</button>


    <br/>
    <br/>
    <button type="submit" name="refreshButton" value="Refresh Button">Refresh</button>

    <br/>
    <br/>

    Default cache entries: <br/>
    <%=c.getStatus().toString().equals("RUNNING") ? c.getAdvancedCache().entrySet().toString() : "Cache is down at this moment."%>

</form>


</body>
</html>



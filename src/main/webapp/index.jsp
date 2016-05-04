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
    <%--CacheManagerLon instance: <%=application.getAttribute("container2")%><br/>--%>
    <%--CacheManagerNyc instance: <%=application.getAttribute("container3")%><br/>--%>

    <%
        EmbeddedCacheManager manager = (EmbeddedCacheManager) application.getAttribute("manager");

        StringBuilder sb = new StringBuilder();
        for (String name : manager.getCacheNames()) {
            sb.append(name + " | ");
        }
    %>

    All caches names excluding ___defaultcache (= started named caches from JON, or by app): <br/> <b><%=sb.toString()%>
    <br/><br/></b>

    <table>
        <tr>

            <%
                // for each cache
                for (String name : manager.getCacheNames()) {
            %>

            <td>

                <%

                    // get cache by name
                    Cache c = manager.getCache(name);


                    if (request.getParameter(name + "addEntry") != null) {
                        int rand = r.nextInt(1000);
                        c.put("key" + rand, "value" + rand);
                    }

                    if (request.getParameter(name + "stopCache") != null) {
                        c.stop();
                    }

                    if (request.getParameter(name + "startCache") != null) {
                        c.start();
                    }

                    if (request.getParameter(name + "clearCache") != null) {
                        c.clear();
                    }

                    if (request.getParameter(name + "resetStatistics") != null) {
                        for (CommandInterceptor interceptor : (List<CommandInterceptor>) c.getAdvancedCache().getInterceptorChain()) {
                            boolean isSubclass = CacheMgmtInterceptor.class.isAssignableFrom(interceptor.getClass());
                            if (isSubclass) {
                                CacheMgmtInterceptor cacheMgmtInterceptor = (CacheMgmtInterceptor) interceptor;
                                cacheMgmtInterceptor.resetStatistics();
                            }
                        }
                    }


                    // stats
                    int currEntries = c.getAdvancedCache().getStats().getCurrentNumberOfEntries();
                    long totalEntries = c.getAdvancedCache().getStats().getTotalNumberOfEntries();
                    String status = c.getStatus().toString();


                    // **********************
                    // for each cache, page contetn:
                %>



                <br/>
                Cache name: <b><u><%=name%></u></b>
                <br/>
                <br/>
                Stats: <br/>
                Current entries:
                <%=currEntries%> <br/>
                Total entries:
                <%=totalEntries%> <br/>
                Cache status:
                <%=status%> <br/> <br/> <br/>

                <%=name%> cache entries: <br/>
                <%=c.getStatus().toString().equals("RUNNING") ? c.getAdvancedCache().entrySet().toString() : "Cache is down at this moment."%>




                <br/>
                <br/>
                <%-- format: cachename + operation name --%>
                <button type="submit" name="<%=name%>addEntry" value="Add Entry Button">Add Entry into Cache</button>
                <button type="submit" name="<%=name%>clearCache" value="Clear Cache Button">CLEAR Cache</button>
                <button type="submit" name="<%=name%>resetStatistics" value="Reset Statistics Button">Reset Cache
                    Statistics
                </button>


                <br/>
                <br/>
                <button type="submit" name="<%=name%>startCache" value="Start Cache Button">START Cache</button>
                <button type="submit" name="<%=name%>stopCache" value="Stop Cache Button">STOP Cache</button>


                <br/>
                <br/>
                <button type="submit" name="refreshButton" value="Refresh Button">Refresh</button>

                <br/>
                <br/>
                <%--Cache configuration: <%=manager.getCacheConfiguration(name).toString()%>--%>

                <br/>
                <br/>

            </td>

            <%
                    // end for getCacheNames()
                }
            %>
        </tr>
    </table>













   

</form>


</body>
</html>



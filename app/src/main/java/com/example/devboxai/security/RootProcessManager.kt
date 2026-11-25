package com.example.devboxai.security

import android.content.Context
import com.example.devboxai.termux.TermuxHelper

object RootProcessManager {
    fun killApp(context: Context, pkg: String): Boolean =
        TermuxHelper.run(context, "su -c 'pkill -9 -f $pkg || true'")
    fun forceStop(context: Context, pkg: String): Boolean =
        TermuxHelper.run(context, "su -c 'am force-stop $pkg'")
    fun freezeApp(context: Context, pkg: String): Boolean =
        TermuxHelper.run(context, "su -c 'pkill -STOP -f $pkg || true'")
    fun unfreezeApp(context: Context, pkg: String): Boolean =
        TermuxHelper.run(context, "su -c 'pkill -CONT -f $pkg || true'")
    fun deprioritize(context: Context, pkg: String): Boolean =
        TermuxHelper.run(context, "su -c 'renice 19 -p \\$(pidof $pkg) 2>/dev/null'")
    fun prioritize(context: Context, pkg: String): Boolean =
        TermuxHelper.run(context, "su -c 'renice -10 -p \\$(pidof $pkg) 2>/dev/null'")
    fun limitCpu(context: Context, pkg: String, cores: Int): Boolean {
        val mask = (1 shl cores) - 1
        val hex = Integer.toHexString(mask)
        return TermuxHelper.run(context,
            "su -c 'echo $hex > /dev/cpuset/background/cpus 2>/dev/null; " +
                "pid=\\$(pidof $pkg); if [ -n \"\\$pid\" ]; then echo \\$pid > /dev/cpuset/background/tasks; fi'")
    }
    fun listTopProcesses(context: Context): Boolean =
        TermuxHelper.runWithResult(context, "su -c 'top -b -n1 -o %CPU | head -30'")
    fun showAppProcesses(context: Context, pkg: String): Boolean =
        TermuxHelper.runWithResult(context, "su -c 'ps -A -o PID,NAME,%CPU,%MEM | grep -i $pkg'")
}

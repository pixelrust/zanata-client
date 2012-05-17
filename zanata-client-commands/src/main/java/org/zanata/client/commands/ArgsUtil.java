/**
 * 
 */
package org.zanata.client.commands;

import java.io.PrintStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

/**
 * @author Sean Flanigan <sflaniga@redhat.com>
 *
 */
public class ArgsUtil
{
   private static final Logger log = LoggerFactory.getLogger(ArgsUtil.class);
   private final AppAbortStrategy abortStrategy;
   private final PrintStream out;
   private final PrintStream err;

   public ArgsUtil(AppAbortStrategy strategy, PrintStream out, PrintStream err)
   {
      this.abortStrategy = strategy;
      this.out = out;
      this.err = err;
   }

   public static void processArgs(String[] args, BasicOptions opts)
   {
      new ArgsUtil(new SystemExitStrategy(), System.out, System.err).process(args, opts);
   }

   public void process(String[] args, BasicOptions opts)
   {
      log.debug("process(args: {}, opts: {})", args, opts);
      CmdLineParser parser = new CmdLineParser(opts);

      try
      {
         parser.setUsageWidth(Integer.parseInt(System.getenv("COLUMNS")));
      }
      catch (Exception e)
      {
         parser.setUsageWidth(120);
      }
      try
      {
         parser.parseArgument(args);
      }
      catch (CmdLineException e)
      {
         if (!opts.getHelp() && args.length != 0)
         {
            err.println(e.getMessage());
            printHelp(opts, err);
            parser.printUsage(err);
            abortStrategy.abort(e);
         }
      }

      if (opts.getHelp() || args.length == 0)
      {
         printHelp(opts, out);
         parser.printUsage(out);
         return;
      }
      // while loading config, we use the global logging options
      setLogLevels(opts);

      try
      {
         if (opts instanceof ConfigurableOptions)
            OptionsUtil.applyConfigFiles((ConfigurableOptions) opts);
         ZanataCommand cmd = opts.initCommand();
         // just in case the logging options were changed by a config file:
         setLogLevels(opts);
         if (opts.getErrors())
         {
            log.info("Error stacktraces are turned on.");
         }
         cmd.run();
      }
      catch (Exception e)
      {
         handleException(e, opts.getErrors(), abortStrategy);
      }
   }

   private static void setLogLevels(BasicOptions opts)
   {
      if (opts.getDebug())
      {
         enableDebugLogging();
      }
      else if (opts.getQuiet())
      {
         enableQuietLogging();
      }
   }

   /**
    * Maven's --debug/-X flag sets the Maven LoggerManager to LEVEL_DEBUG. The
    * slf4j framework doesn't provide any way of doing this, so we have to go to
    * the underlying framework (assumed to be logback).
    */
   private static void enableDebugLogging()
   {
      getRootLogger().setLevel(Level.DEBUG);
   }

   /**
    * Maven's --quiet/-q flag sets the Maven LoggerManager to LEVEL_ERROR. The
    * slf4j framework doesn't provide any way of doing this, so we have to go to
    * the underlying framework (assumed to be logback).
    */
   private static void enableQuietLogging()
   {
      getRootLogger().setLevel(Level.ERROR);
   }

   private static ch.qos.logback.classic.Logger getRootLogger()
   {
      // TODO move ZanataClient and ArgsUtil to a new uberjar module, which has logback as a 'compile' dependency
      return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
   }

   private static void printHelp(BasicOptions cmd, PrintStream output)
   {
      output.println("Usage: " + cmd.getCommandName() + " [options]");
      output.println(cmd.getCommandDescription());
      output.println();
   }

   public static void handleException(Exception e, boolean outputErrors, AppAbortStrategy abortStrategy)
   {
      if (outputErrors)
      {
         log.error("Execution failed: ", e);
      }
      else
      {
         log.error("Execution failed: " + e.getMessage());
         log.error("Use -e/--errors for full stack trace (or when reporting bugs)");
      }
      abortStrategy.abort(e);
   }

}

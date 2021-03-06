/*
 * JVM agent to track memory allocations
 *
 * Copyright (C) 2018 Jesper Pedersen <jesper.pedersen@comcast.net>
 */
package org.jboss.javamemflame;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

/**
 * Main class that creates the meminfo-pid.txt file
 */
public class Main
{
   /**
    * Write data to a file
    * @param p The path of the file
    * @param l The data
    */
   private static void writeFile(Path p, List<String> l) throws Exception
   {
      BufferedWriter bw = Files.newBufferedWriter(p,
                                                  StandardOpenOption.CREATE,
                                                  StandardOpenOption.WRITE,
                                                  StandardOpenOption.TRUNCATE_EXISTING);
      for (String s : l)
      {
         bw.write(s, 0, s.length());
         bw.newLine();
      }

      bw.flush();
      bw.close();
   }

   /**
    * Translate from byte code name to human readable name
    * @param input The input
    * @return Human readable
    */
   private static String translate(String input)
   {
      int array = 0;
      int i = 0;

      StringBuilder sb = new StringBuilder();
   
      while (input.charAt(i) == '[')
      {
         array++;
         i++;
      }
   
      if (input.charAt(i) == 'Z')
      {
         sb.append("boolean");
      }
      else if (input.charAt(i) == 'B')
      {
         sb.append("byte");
      }
      else if (input.charAt(i) == 'C')
      {
         sb.append("char");
      }
      else if (input.charAt(i) == 'D')
      {
         sb.append("double");
      }
      else if (input.charAt(i) == 'F')
      {
         sb.append("float");
      }
      else if (input.charAt(i) == 'I')
      {
         sb.append("int");
      }
      else if (input.charAt(i) == 'J')
      {
         sb.append("long");
      }
      else if (input.charAt(i) == 'S')
      {
         sb.append("short");
      }
      else if (input.charAt(i) == 'L')
      {
         sb.append(input.substring(i + 1));
      }
      else
      {
         sb.append(input.substring(i));
      }

      for (int array_counter = 0; array_counter < array; array_counter++)
      {
         sb.append("[]");
      }
   
      return sb.toString();
   }

   /**
    * main
    * @parameter args The program arguments
    */
   public static void main(String[] args)
   {
      try
      {
         if (args == null || args.length < 1)
         {
            System.out.println("javamemflame: Recording flamegraph data for Java memory allocations");
            System.out.println("");
            System.out.println("Usage: java -jar javamemflame.jar <file_name> [include[,include]*]");

         }

         String file = args[0];
         Path path = Paths.get(file);
         long pid = Long.valueOf(file.substring(file.indexOf("-") + 1, file.indexOf(".")));
         Set<String> includes = null;

         if (args.length > 1)
         {
            includes = new HashSet<>();

            StringTokenizer st = new StringTokenizer(args[1], ",");
            while (st.hasMoreTokens())
            {
               String include = st.nextToken();
               include = include.replace('.', '/');
               includes.add(include);
            }
         }

         
         List<String> l = new ArrayList<>();
         RecordingFile rcf = new RecordingFile​(path);
                  
         while (rcf.hasMoreEvents())
         {
            RecordedEvent re = rcf.readEvent();
            if (re.hasField("stackTrace"))
            {
               RecordedStackTrace st = (RecordedStackTrace)re.getValue("stackTrace");

               if (st != null)
               {
                  List<RecordedFrame> lrf = st.getFrames();
                  if (lrf != null && lrf.size() > 0)
                  {
                     StringBuilder sb = new StringBuilder();
                     sb.append("java;");

                     for (int i = lrf.size() - 1; i >= 0; i--)
                     {
                        RecordedFrame rf = st.getFrames().get(i);
                        RecordedMethod rm = rf.getMethod();
                        RecordedClass rc = rm.getType();
                        sb.append(rc.getName().replace('.', '/'));
                        sb.append(":.");
                        sb.append(rm.getName());
                        sb.append(";");
                     }

                     RecordedClass rc = (RecordedClass)re.getValue("objectClass");
                     sb.append(translate(rc.getName()));
                     sb.append(" ");
                     sb.append(re.getLong("allocationSize"));
               
                     String entry = sb.toString();

                     if (includes == null)
                     {
                        l.add(entry);
                     }
                     else
                     {
                        for (String include : includes)
                        {
                           if (entry.contains(include))
                           {
                              l.add(entry);
                              break;
                           }
                        }
                     }
                  }
               }
            }
         }

         rcf.close();
         
         if (l.size() > 0)
         {
            writeFile(Paths.get("mem-info-" + pid + ".txt"), l);
         }
         else
         {
            System.out.println("No data detected for: " + file);
         }
      }
      catch (Exception e)
      {
         System.err.println(e.getMessage());
         e.printStackTrace();
      }
   }
}

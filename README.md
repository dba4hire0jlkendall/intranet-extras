Intranet Extras
===============

Intranet Extras provides utilities that you can deploy and use easily on your server. 
For example: you can configure the time to run a job to clean your Trash folder automatically, so that after a pre-defined time period, your Trash is going to be cleaned up.

How to build and deploy extra modules provided by eXo Platform.

* Requirements:
  + Git client
  + Java Development Kit 1.6
  + Maven 3

* Get the source code of Extra project from Github and build

<pre><code>
  git clone git@github.com:exo-addons/intranet-extras.git
  cd intranet-extras
  mvn install
</code></pre>

* After a build success of the project you will have

<pre><code>
  + ${PROJECT_HOME}/component/jobSchedule/target/extra-component-jobSchedule-${PROJECT_VERSION}.jar
  + ${PROJECT_HOME}/ear/target/extra-modules.ear
</code></pre>

You just have to copy respectively the jar package to lib folder of your tomcat server and ear package to server/default/deploy folder of jboss server.

And the final step is adding this property in the file that contains all your system configuration and startup server:

<pre><code>
  #Expired time of files in trash, measure by day
  valid.time.in.trash=30
</code></pre>

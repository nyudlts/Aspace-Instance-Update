# Aspace-Instance-Update
This application is used to batch update Top Container URIs (box numbers) and Child Indicators (folder numbers) for Archival Object's within a Resource. The application uses the work order output from the [Hudson Molonglo's Digitization Work Order Plugin](https://github.com/hudmol/digitization_work_order).

Prerequisites
-------------
* java8
* sbt v1.2.8 or greater


Build
------------------
* git clone https://github.com/NYU-ACM/Aspace-Instance-Update.git
* cd Aspace-Instance-Update
* vi src/main/resources and add your environements' info to the config
* sbt assembly -- this will generate a jar file in target/scala-2.12
* cp target/scala-2.12/ASInstanceUpdate.jar .

Work-Order Specification
------------------------
forthcoming

Run
---
* java -jar AspaceInstanceUpdate.jar -e dev -s my-workorder.tsv

Options
-------
* -s, --source, required	/path/to/workorder.tsv
* -e, --env, required		aspace environment to be used: dev/stage/prod
* -u, --undo, optional	runs a work order in revrse, undo a previous run
* -t, --test, optional	test mode does not execute any POSTs, this is recommended before running on any data
* -h, --help	print this help message

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
* cp src/main/resources/application.conf_template src/main/resources/application.conf
* vi src/main/resources/application.cong add your environements' info to the config
* sbt assembly -- this will generate a jar file in target/scala-2.12
* cp target/scala-2.12/ASInstanceUpdate.jar .

Work-Order Specification
------------------------
| Resource ID	| Ref ID	| URI	| Container Indicator 1	| Container Indicator 2	| Container Indicator 3	| Title	| Component ID |
| ---	| ---	| ---| ---	| --- | --- | ---	| --- |
| TAM.011	| ref14	| /repositories/2/archival_objects/154967	| 1 | 	1 |  | Correspondence	| |

In a spreadsheet editor add two columns to the work order: 'New Container Indicator 1' and 'New Container Indicator 2'. The updater will update the Instances updating the reference to the top container and the child indicator. 

| Resource ID	| Ref ID	| URI	| Container Indicator 1	| Container Indicator 2	| Container Indicator 3	| Title	| Component ID | New Container Indicator 1	| New Container Indicator 2 |
| ---	| ---	| ---| ---	| --- | --- | ---	| --- | ---	| --- |
| TAM.011	| ref14	| /repositories/2/archival_objects/154967	| 1 | 	1 |  | Correspondence	| | 2 | 1 |

Run
---
* java -jar AspaceInstanceUpdate.jar -e dev -s my-workorder.tsv

Important Notes
---------------
* This program does not create any new top containers, they must exist and attached to a published Archival Object. This is a limitation of the Aspace API

Options
-------
* -s, --source, required	/path/to/workorder.tsv
* -e, --env, required		aspace environment to be used: dev/stage/prod
* -u, --undo, optional	runs a work order in revrse, undo a previous run
* -t, --test, optional	test mode does not execute any POSTs, this is recommended before running on any data
* -h, --help	print this help message

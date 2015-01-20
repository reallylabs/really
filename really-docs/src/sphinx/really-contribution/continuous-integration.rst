Continuous Integration
======================

Each project should be configured to use a continuous integration (CI) tool. The CI tool should, on each push to master, build the **full** distribution and run **all tests**, and if something fails it should email out a notification with the failure report to the committer and the core team. 
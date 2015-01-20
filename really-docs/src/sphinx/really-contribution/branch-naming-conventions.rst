Branch naming conventions
=========================

* The branch should be given a descriptive name that explains its intent. 
* Define and use short lead tokens to differentiate branches in a way that is meaningful to your workflow.
* se hyphens to separate parts of your branch names.
* Do not use bare numbers as leading parts.
* Avoid long descriptive names for long-lived branches.

Short well-defined tokens
-------------------------

Choose short tokens so they do not add too much noise to every one of your branch names. for example:

+------------+---------------------------------------------------------+
| Name       | Description                                             |
+============+=========================================================+
| wip        | Works in progress stuff I know won't be finished soon   |
+------------+---------------------------------------------------------+
| junk       | Throwaway branch created to experiment                  |
+------------+---------------------------------------------------------+
| feat       | Feature I'm adding or expanding                         |
+------------+---------------------------------------------------------+
| bug        | Bug fix or experiment                                   |
+------------+---------------------------------------------------------+

Each of these tokens can be used to tell you to which part of your workflow each branch belongs. You can name your branches with abbreviated versions of these tags, always spelled the same way, to both group them and to remind you which stage you're in.
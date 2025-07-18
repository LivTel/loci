# source environment.csh on loci1, using a tcsh SHELL
set env_dir = `pwd`
if ( ${?LD_LIBRARY_PATH} ) then
    setenv LD_LIBRARY_PATH ${LD_LIBRARY_PATH}:${env_dir}
else
    setenv LD_LIBRARY_PATH ${env_dir}:.
endif

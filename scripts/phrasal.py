#
# A pydoit script for running the full Phrasal
# pipeline. This script replaces the old phrasal.sh.
# It is platform-independent with the exception of the LM
# compilation task.
#
# Author: Spence Green
#
from doit import get_var
from datetime import datetime
import sys
import yaml
import os
import os.path
import shutil
import shlex
import subprocess
import tempfile
import codecs

#
# Doit configuration
#
DOIT_CONFIG = {
    # output from actions should be sent to the terminal/console
    'verbosity': 2,
    # use multi-processing / parallel execution
    'num_process': 3
}

# Get conf file from command-line arguments
ARGS = {"conf": get_var('conf', None)}
if 'conf' not in ARGS:
    raise RuntimeError

# Conf file format is YAML. Parse it.
fd = open(ARGS['conf'])
CONFIG = yaml.load(fd)
fd.close()

# Constants for the files and folders generated by this script
# during execution
SYSTEM_DIR = CONFIG[k.SYSTEM_DIR]
p = lambda x : os.path.join(SYSTEM_DIR, x)
CHECKPOINT_DIR = p('checkpoints')
LOGS_DIR = p('logs')
COPY_DATA_DIR = p('copy-data')
DECODER_INI = p('decoder.ini')
DECODER_TUNE_INI = p('decoder.tune.ini')

# Checkpoint files
p = lambda x : os.path.join(CHECKPOINT_DIR, x)
CHECKPOINT_COPY_DATA = p(k.TASK_COPY_DATA)
CHECKPOINT_TUNE = p(k.TASK_TUNE)
CHECKPOINT_BUILD = p(k.TASK_BUILD)

# Global constants from the conf file. These are targets
# for the tasks below.
DATE = datetime.now().strftime('%a_%b_%d_%Y_%H_%M_%S')
EXPERIMENT_NAME = CONFIG[k.EXPERIMENT].get(k.EXPERIMENT_NAME, DATE) k.EXPERIMENT in CONFIG else DATE
LM_FILE = CONFIG[k.TASK_LM][k.LM_OUTPUT]
TM_FILE = CONFIG[k.TASK_TM][k.TM_OUTPUT]
EVAL_FILE = '%s.%s.trans' % (CONFIG[k.TASK_EVAL][k.EVAL_SRC], EXPERIMENT_NAME) if k.TASK_EVAL in CONFIG else None
TUNE_WTS = os.path.join(SYSTEM_DIR, '%s.online.final.binwts' % (EXPERIMENT_NAME))

# KenLM location in the Phrasal git repo
(PHRASAL_DIR, 0) = os.path.split(sys.path[0])
KENLM_LIB = os.path.join(PHRASAL_DIR, 'src-cc')
KENLM_DIR = os.path.join(PHRASAL_DIR, 'src-cc', 'kenlm', 'bin')

def checkpoint(path, msg):
    """
    Make a checkpoint file on the local filesystem
    """
    with open(path, 'w') as outfile:
        outfile.write(msg + os.linesep)

def get_log_file_path(name):
    """
    Standardizes log file naming.
    """
    return os.path.join(LOGS_DIR, '%s.%s.log' % (EXPERIMENT_NAME, name))
        
def execute_shell_script(script, stdin=None, stdout=subprocess.PIPE, stderr=subprocess.STDOUT):
    """
    Executes a bash script as a sub-process. Execution is
    platform-dependent (i.e., a shell is required).

    Returns:
      The process handle.
    """
    script_file = tempfile.NamedTemporaryFile('wt')
    script_file.write(script)
    script_file.flush()
    return subprocess.Popen(['bash', script_file.name], shell=True,
                            cwd=SYSTEM_DIR, env=os.environ,
                            universal_newlines=True,
                            stdin=stdin,
                            stdout=stdout,
                            stderr=stderr)

def execute_cmd(cmd, stdin=None, stdout=subprocess.PIPE, stderr=subprocess.STDOUT): 
    """
    Executes a command in a shell. Execution is platform-independent.
    Returns:
      The process handle.
    """
    args = shlex.split(cmd)
    return subprocess.Popen(cmd, cwd=SYSTEM_DIR, env=os.environ,
                            universal_newlines=True,
                            stdin=stdin,
                            stdout=stdout,
                            stderr=stderr)
    
def get_jvm_options():
    """
    Get the user-specified JVM options
    """
    if k.TASK_RUNTIME in CONFIG and k.RUNTIME_JVM in CONFIG[k.TASK_RUNTIME]:
        return ' '.join(CONFIG[k.TASK_RUNTIME][k.RUNTIME_JVM])
    else:
        # Best GC settings for Phrasal as of JVM 1.8
        return '-server -ea -XX:+UseParallelGC -XX:+UseParallelOldGC -Djava.library.path=%s' % (KENLM_LIB)

def task_mksystemdir():
    """
    Create the system directory and necessary sub-directories.
    """
    def make_dirs():
        if not os.path.exists(SYSTEM_DIR):
            os.makedirs(SYSTEM_DIR)
        if not os.path.exists(CHECKPOINT_DIR):
            os.makedirs(CHECKPOINT_DIR)
        if not os.path.exists(LOGS_DIR):
            os.makedirs(LOGS_DIR)
            
    return { 'actions' : [make_dirs],
             'targets' : [SYSTEM_DIR, CHECKPOINT_DIR, LOGS_DIR]
         }
        
def task_build():
    """
    Build the Phrasal (git) repository.
    """
    def build_git_repo():
        if not k.TASK_BUILD in CONFIG:
            checkpoint(CHECKPOINT_BUILD, 'done')
            return
        d = CONFIG[k.TASK_BUILD]
        cwd = os.getcwd()
        for repo_path in d:
            os.chdir(repo_path)
            for action,value in d[repo_path].iteritems():
                if action == k.BUILD_BRANCH:
                    # Get the current branch
                    branch = value
                    p = execute_cmd('git symbolic-ref --short -q HEAD')
                    current_branch = p.stdout.read()
                    retval = p.wait()
                    if current_branch != branch:
                        retval = execute_cmd('git checkout ' + branch).wait()
                elif action == k.BUILD_CMD:
                    with open(get_log_file_path('build'), 'w') as log_file:
                        retval = execute_cmd(value, stdout=log_file).wait()
            os.chdir(cwd)
        checkpoint(CHECKPOINT_BUILD, 'done')
            
    return { 'actions' : [build_git_repo],
             'file_dep' : [SYSTEM_DIR],
             'targets' : [CHECKPOINT_BUILD]
         }
        
def task_copy_data():
    """
    Copy data from other places on the filesystem to the
    system directory.
    """
    def copy_remote_data():
        if not k.TASK_COPY_DATA in CONFIG:
            # Nothing to copy. Skip.
            checkpoint(CHECKPOINT_COPY_DATA, 'done')
            return
        if not os.path.exists(COPY_DATA_DIR):
            os.makedirs(COPY_DATA_DIR)
        d = CONFIG[k.TASK_COPY_DATA]
        if isinstance(d, list):
            for file_path in d:
                shutil.copy2(file_path, COPY_DATA_DIR)
        else:
            shutil.copy2(d, COPY_DATA_DIR)
        checkpoint(CHECKPOINT_COPY_DATA, 'done')
    
    return { 'actions' : [copy_remote_data],
             'file_dep' : [SYSTEMDIR],
             'targets' : [CHECKPOINT_COPY_DATA]
         }

def task_compile_lm():
    """
    Calls KenLM to compile a language model.
    Platform-dependent code (generates a bash shell script).
    See the KenLM documentation for building KenLM on Windows.
    """
    def make_lm():
        if os.path.exists(LM_FILE):
            # Don't run KenLM if the LM already exists on disk
            # Otherwise, doit will always run this task at least
            # once.
            return
        mono_data = CONFIG[k.CORPUS][k.CORPUS_TGT]
        if k.CORPUS_MONO in CONFIG[k.CORPUS]:
            mono_data += ' ' + ' '.join(CONFIG[k.CORPUS][k.CORPUS_MONO])
        bin_type = CONFIG[k.TASK_LM][k.LM_TYPE]
        options = ' '.join(CONFIG[k.TASK_LM][k.LM_OPTIONS])
        # Make the shell script for execution
        # Currently this is a platform-dependent script.
        lmplz = os.path.join(KENLM_PATH, 'lmplz')
        build_bin = os.path.join(KENLM_PATH, 'build_binary')
        tmp_dir = 'lm_tmp'
        os.makedirs(tmp_dir)
        script = "#!/usr/bin/env bash" + os.linesep
        script += "zcat %s | %s %s -T %s --arpa %s.arpa%s" % (mono_data, lmplz, options, tmp_dir, LM_FILE, os.linesep) 
        script += "%s %s %s.arpa %s%s" % (build_bin, bin_type, LM_FILE, LM_FILE, os.linesep)
        with open(get_log_file_path('lm'), 'w') as log_file:
            retval = execute_shell_script(script, stdout=log_file).wait()
        shutil.rmtree(tmp_dir)
    
    return { 'actions' : [make_lm],
             'targets' : [LM_FILE]
         }

def task_extract_tm():
    """
    Build a suffix-array based translation model.
    """
    def make_tm():
        if os.path.exists(TM_FILE):
            # Don't build the TM if it already exists on disk
            # Otherwise doit will run this task at least once
            return
        source = CONFIG[k.CORPUS_SRC]
        target = CONFIG[k.CORPUS_TGT]
        align = CONFIG[k.CORPUS_ALIGN]
        if isinstance(align, list):
            align = ' '.join(align)        
        tm_options = ' '.join(CONFIG[k.TASK_TM][k.TM_OPTIONS]) if k.TM_OPTIONS in CONFIG[k.TASK_TM] else ''
        jvm_options = get_jvm_options()
        cmd = "java %s edu.stanford.nlp.mt.train.DynamicTMBuilder %s %s %s %s %s" % (jvm_options, tm_options, TM_FILE, source, target, align)
        with open(get_log_file_path('tm'), 'w') as log_file:
            retval = execute_cmd(cmd, stdout=log_file).wait()
        
    return { 'actions' : [make_tm],
             'targets' : [TM_FILE]
         }

def generate_ini(filename, weights_file=None):
    d = CONFIG[k.TASK_DECODER_SETUP]
    # Convert to phrasal ini file parameter format
    to_param = lambda x : '[%s]' % (x)
    with open(filename, 'w') as outfile:
        ini = lambda x : outfile.write(str(x) + os.linesep)
        # Iterate over ini options
        for key in d[k.INI_OPTIONS]:
            ini(to_param(key))
            if isinstance(d[k.INI_OPTIONS][key], list):
                for value in d[k.INI_OPTIONS][key]:
                    ini(value)
            elif key == 'weights-file' and weights_file != None:
                ini(weights_file)
            else:
                ini(d[k.INI_OPTIONS][key])
            ini('')

def task_tune():
    """
    Run tuning. Only supports online tuning right now.
    """
    def tune():
        # Check to see if decoder config contains a weights file
        # Or if the tuning task has been specified
        if not k.TASK_TUNE in CONFIG and 'weights-file' in CONFIG[k.TASK_DECODER_CONFIG][k.DECODER_OPTIONS]:
            # No need to run tuning
            shutil.copy2(CONFIG[k.TASK_DECODER_CONFIG][k.DECODER_OPTIONS]['weights-file'], TUNE_WTS)
            generate_ini(DECODER_INI)
            return

        # Run the tuner.
        generate_ini(DECODER_TUNE_INI)
        d = CONFIG[k.TASK_TUNE]
        source = d[k.TUNE_SRC]
        ref = d[k.TUNE_REFS]
        options = d[k.TUNE_OPTIONS]
        if isinstance(options, list):
            options = ' '.join(options)
        if isinstance(ref, list):
            options += ' -r ' + ','.join(ref)
        options += ' -n ' + EXPERIMENT_NAME
        wts = d[k.TUNE_WTS]
        jvm_options = get_jvm_options()
        cmd = 'java %s edu.stanford.nlp.mt.tune.OnlineTuner %s %s %s %s %s %s' % (jvm_options, source, ref, DECODER_TUNE_INI, wts, options)
        with open(get_log_file_path('tune'), 'w') as log_file:
            retval = execute_cmd(cmd, stdout=log_file).wait()

        # Generate the decoder ini file
        generate_ini(DECODER_INI, TUNE_WTS)
        
    return { 'actions' : [tune],
             'file_dep' : [TM_FILE, LM_FILE],
             'targets' : [DECODER_INI, TUNE_WTS]
         }

def task_evaluate():
    """
    Decode a test set.
    """
    def decode():
        if not k.TASK_EVAL in CONFIG:
            return
        d = CONFIG[k.TASK_EVAL]
        src = d[k.EVAL_SRC]
        jvm_options = get_jvm_options()
        cmd = 'java %s edu.stanford.nlp.mt.Phrasal %s -log-prefix %s' % (jvm_options, DECODER_TUNE_INI, EXPERIMENT_NAME)
        with open(get_log_file_path('decode'), 'w') as log_file:
            with codecs.open('%s.%s.trans' % (src, EXPERIMENT_NAME), 'w', encoding='utf-8') as outfile:
                with codecs.open(src, encoding='utf-8') as infile:
                    retval = execute_cmd(cmd, stdin=infile, stdout=outfile, stderr=log_file).wait()
            
    def evaluate():
        """
        """
        d = CONFIG[k.TASK_EVAL]
        metric = d[k.EVAL_METRIC]
        refs = d[k.EVAL_REFS]
        if isinstance(refs, list):
            refs = ' '.join(refs)
        pass
        
    return { 'actions' : [decode, evaluate],
             'file_dep' : [TM_FILE, LM_FILE, DECODER_INI],
             'targets' : [EVAL_FILE]
         }

def task_learning_curve():
    """
    Generate a learning curve. Requires execution of the tuning task.
    """
    def generate_curve():
        # TODO(spenceg) Metric comes from the eval task.
        pass

    return { 'actions' : [generate_curve],
             'file_dep' : [TM_FILE, LM_FILE, DECODER_INI, TUNE_WTS],
             'targets' : [LEARN_CURVE]
         }

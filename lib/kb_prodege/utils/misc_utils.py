import uuid
import os
from installed_clients.AssemblyUtilClient import AssemblyUtil
from installed_clients.DataFileUtilClient import DataFileUtil
from installed_clients.KBaseReportClient import KBaseReport
from installed_clients.MetagenomeUtilsClient import MetagenomeUtils
from installed_clients.WorkspaceClient import Workspace
from shutil import copyfile


# copied from https://github.com/kbaseapps/kb_cmash/blob/master/lib/kb_cmash/utils/misc_utils.py
# small modifications


def load_fastas(config, scratch, upa):
    '''
    '''
    dfu = DataFileUtil(config['callback_url'])
    au = AssemblyUtil(config['callback_url'])
    mgu = MetagenomeUtils(config['callback_url'])
    ws = Workspace(config['workspace-url'])

    obj_data = dfu.get_objects({"object_refs": [upa]})['data'][0]
    obj_type = obj_data['info'][2]

    if 'KBaseSets.GenomeSet' in obj_type:
        upas = [gsi['ref'] for gsi in obj_data['data']['items']]
    elif 'KBaseSearch.GenomeSet' in obj_type:
        upas = [gse['ref'] for gse in obj_data['data']['elements'].values()]
    elif "KBaseGenomes.Genome" in obj_type:
        upas = [upa]
    elif "KBaseGenomes.ContigSet" in obj_type or "KBaseGenomeAnnotations.Assembly" in obj_type:
        # in this case we use the assembly file util to get the fasta file
        # file_output = os.path.join(scratch, "input_fasta.fa")
        faf = au.get_assembly_as_fasta({"ref": upa})
        return [(faf['path'], upa)]
    elif "KBaseSets.AssemblySet" in obj_type:
        fasta_paths = []
        for item_upa in obj_data['data']['items']:
            faf = au.get_assembly_as_fasta({"ref": item_upa['ref']})
            fasta_paths.append((faf['path'], item_upa['ref']))
        return fasta_paths
    elif 'KBaseMetagenomes.BinnedContigs' in obj_type:
        fasta_paths = []
        bin_file_dir = mgu.binned_contigs_to_file({'input_ref': upa, 'save_to_shock': 0})['bin_file_directory']
        for (dirpath, dirnames, filenames) in os.walk(bin_file_dir):
            for fasta_file in filenames:
                fasta_path = os.path.join(scratch, fasta_file)
                fasta_path = os.path.splitext(fasta_path)[0] + ".fa"
                copyfile(os.path.join(bin_file_dir, fasta_file), fasta_path)
                # Should I verify that the bins have contigs?
                # is it possible to have empty bins?
                fasta_paths.append((fasta_path, upa))
            break
        return fasta_paths

    fasta_paths = []
    for genome_upa in upas:
        genome_data = ws.get_objects2({'objects': [{"ref": genome_upa}]})['data'][0]['data']
        assembly_upa = genome_upa + ';' + str(genome_data.get('contigset_ref') or genome_data.get('assembly_ref'))
        faf = au.get_assembly_as_fasta({'ref': assembly_upa})
        fasta_paths.append((faf['path'], assembly_upa))

    return fasta_paths


def rename_input_file_suffixes(scratch):
    d = scratch
    for path in os.listdir(d):
        full_path = os.path.join(d, path)
        if full_path.endswith(".fa") or full_path.endswith(".fna"):
            new_filename = os.path.join(d, "_".join(full_path.split(".")[0:-1]) + ".fasta")
            os.rename(full_path, new_filename)


def create_html_report(callback_url, scratch, workspace_name):
    '''
    '''
    output_dir = os.path.join(scratch, 'output')
    dfu = DataFileUtil(callback_url)
    report_name = 'METABOLIC_report_' + str(uuid.uuid4())
    report = KBaseReport(callback_url)
    copyfile(os.path.join(os.path.dirname(__file__), 'report_template.html'),
             os.path.join(output_dir, 'report_template.html'))

    report_shock_id = dfu.file_to_shock({'file_path': output_dir,
                                        'pack': 'zip'})['shock_id']

    html_file = {'shock_id': report_shock_id,
                 'name': 'report_template.html',
                 'label': 'report_template.html',
                 'description': 'HTML report for METABOLIC'
                 }

    report_info = report.create_extended_report({'direct_html_link_index': 0,
                                                 'html_links': [html_file],
                                                 'report_object_name': report_name,
                                                 'workspace_name': workspace_name
                                                 })
    return {
        'report_name': report_info['name'],
        'report_ref': report_info['ref']
    }

#!/usr/bin/env perl
#ProDeGe Copyright (c) 2014, The Regents of the University of California,
#through Lawrence Berkeley National Laboratory (subject to receipt of any
#required approvals from the U.S. Dept. of Energy).  All rights reserved.

use strict;
use warnings;

my $usage="$0 <directory which contains input fasta file> <IMG_taxonomy file> <jobname>\n";
@ARGV==3 or die $usage;
my $jobname=$ARGV[2];
my $int_dir=$ARGV[0] . "/" . $jobname . "_Intermediate/";
my $contigbin=$int_dir . $jobname . "_contigs.bins";
my $taxfile=$ARGV[1];
my $outfile=$int_dir . $jobname . "_contigs.LCA";
my $outcsfile=$int_dir . $jobname . "_contigs.species";
my $statsfile=$int_dir . $jobname . "_genes.blout.stats";
unless(-e $contigbin) {die "$contigbin does not exist.\n";}
unless(-e $taxfile) {die "$taxfile does not exist.\n";}
open(OUT,">$outfile");
open(IN,$contigbin);
open(OUTCS,">$outcsfile");

#Begin add for Issue #37
#my %stats;
#open(STA,$statsfile);
#my $line=<STA>;
#while($line=<STA>){
#	chomp($line);
# 	my @a=split(/\t/,$line);
# 	if(($a[3]>=.5 and $a[1]>2) or ($a[3]>=1 and $a[1]==2)){
# 		$stats{$a[0]}=1;
# 	}	
#}	
#close(STA);
#End add for issue #37

my %tts;
#my $cutoff=.75;
my $cutoff=0.6;
#my $cutoff=0.5000000001;
my $cutoff_10=0.6;

while(my $line=<IN>){
      chomp $line;
      my @arr=split(/\t/,$line);
#      next if (!exists($stats{$arr[0]})); #Add for issue 37
      if(defined($arr[2])){
	my @arr2=split(/,/,$arr[2]);
	my %tree;
	my %etree;
	my $start=2;
        $tree{'root'}{'p'}='root';
        $tree{'root'}{'nc'}=0;
	$tree{'root'}{'tc'}=0;
	print OUTCS "$arr[0]";
	foreach my $tax (@arr2){
#	  unless($tax=~/candidate division/ or $tax=~/unclassified candidate/){
        	$tax=~s/\s$//g;
		my $cmd="grep " . $tax . " " . $taxfile;
		my @nts=`$cmd`;
		my $pnode='root';
		foreach my $ctax (@nts){
			chomp($ctax);
			$ctax=~s/^\d+\t//; #change for img db
			$ctax="root\t" . $ctax;
			my $ostr=$ctax;
			$ostr=~s/\t/;/g;
			print OUTCS "," . $ostr;
			my @arr3=split(/\t/,$ctax);
			foreach my $node (@arr3){
				if($start==2){
					$tree{$node}{'p'}='root';
					$start=1;
				}
                        	elsif($start==1){
                        		$tree{$pnode}{'1'}=$node;
					$tree{$node}{'p'}=$pnode;
					$tree{$pnode}{'nc'}=1;
					$tree{$pnode}{'tc'}=$tree{$pnode}{'tc'}+1;
					$tree{$node}{'nc'}=0;
					$tree{$node}{'tc'}=0;
				}
				else{	
					if(exists($etree{$node})){
						if($node!~/root/){
							$tree{$pnode}{'tc'}=$tree{$pnode}{'tc'}+1;
						}
					}
					else{
                                                $tree{$pnode}{'nc'}=$tree{$pnode}{'nc'}+1;
						$tree{$pnode}{$tree{$pnode}{'nc'}}=$node;
                                                $tree{$pnode}{'tc'}=$tree{$pnode}{'tc'}+1;
						$tree{$node}{'p'}=$pnode;
						$tree{$node}{'nc'}=0;
                                                $tree{$node}{'tc'}=0;
					}	
				}
				$pnode=$node;
				$etree{$node}=1;

			}
			$start=0;
			last;
		}
#	  }
	}
	print OUTCS "\n";
        my $found=1;
        my $node="root";
        my $str=" ";
        while($found==1 and $tree{$node}{'nc'}>0){
		#print "node=$node,nc=$tree{$node}{'nc'},tc=$tree{$node}{'tc'},p=$tree{$node}{'p'}\n";
		$found=0;
		$str= $str . $node . ";";
		if($tree{$node}{'nc'}>1){
			for(my $i=1;$i<=$tree{$node}{'nc'};$i++){
        		        #print "node=$node,nc=$tree{$node}{'nc'},tc=$tree{$node}{'tc'},p=$tree{$node}{'p'},ptc=$tree{$tree{$node}{$i}}{'tc'}\n";
				my $coff;
				if($tree{$node}{'tc'}>=10){
					$coff=$cutoff;
				}else{
					$coff=$cutoff_10;
				}
#				if(($tree{$tree{$node}{$i}}{'tc'}/$tree{$node}{'tc'})>=$coff){
                                if(($tree{$tree{$node}{$i}}{'tc'}/$tree{$node}{'tc'})>$coff){
       				        $node=$tree{$node}{$i};
					$found=1;
					last;
				}		
			}
			if($found==0){
				last;	
			}
		}
		else{
			$node=$tree{$node}{'1'};
			$found=1;
		}
        }
        $tts{$arr[0]}=$str;
	$str=~s/^ root;//;
	#if($str=~/unclassi/){
	#	$str=~s/unclassi.*//;
 	#}	
        print OUT "$arr[0]\t$str\n";
      }
      else{
	print OUT "$arr[0]\t\n";
      }
}
close(IN);
close(OUT);
close(OUTCS);
1;

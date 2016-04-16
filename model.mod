param n_inner;
param n_cons; #Number of consumer nodes
param n_tgen; #number of convential generators
param n_rgen; #number of renewable generators
param n_storage; #number of storage nodes
param n_tot; # total number of nodes.
param m_factor; #multiplication factor per-unit MW
param pi; #pi constant
param totload; #total demand for consume rnodes
param c_curt; #renewable cut costs
param outname;

#initialize variables 
set nodes := 0..n_tot;
set tgen := 0..n_tgen;
set consumers := n_tgen+1..(n_tgen+n_cons);
set inner := (n_tgen+n_cons+1)..(n_tgen+n_cons+n_inner);
set rgen :=  (n_tgen+n_cons+n_inner+1)..(n_tgen+n_cons+n_inner+n_rgen);
set storage := (n_tgen+n_cons+n_inner+n_rgen+1)..n_tot;

param weight {nodes,nodes} >=0;
param capacity {nodes,nodes} >=0;
param costs {tgen} >=0, default 0;
param rcost {rgen} >=0, default 0;
param rprodmax {rgen} >=0;
param rprodmin {rgen} >=0;
param storagemin {storage};
param storagemax {storage};
param mintprod {tgen} >=0;
param maxtprod {tgen} >=0;
param loads {consumers} >=0;

#phase angle constraint 
var theta {nodes} >= -pi/2, <= pi/2;

#The function to minimize daily system cost.
minimize obj :	(sum{i in tgen} ((sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/ weight[i,j] )*costs[i])) + 
		(sum{i in rgen} ((sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/ weight[i,j] )*rcost[i])) + 
		(sum{i in rgen} c_curt*(rprodmax[i]-(sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j])));

#Subject to these constrains
subject to anglestability :
	theta[46], = 0; 

#Maximum flow rate
subject to flowcapmax { i in nodes,j in nodes : capacity[i,j] <> 0} :
	((theta[i]-theta[j])/weight[i,j])*m_factor, <= capacity[i,j];

#Minimum flow rate
subject to flowcapmin { i in nodes,j in nodes : capacity[i,j] <> 0} :
	((theta[i]-theta[j])/weight[i,j])*m_factor, >= -capacity[i,j];

#Minimum generation of a renewable node
subject to flowcons { i in inner } :
	sum{ j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = sum{ j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor;

#Max generation of a renewable node
subject to rgenmin { i in rgen } :
	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= rprodmin[i];

#Minimum generation of a conventional node
subject to rgenmax { i in rgen } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= rprodmax[i];

#Maximum generation of a conventional node
subject to genmin { i in tgen } :
	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= mintprod[i];

#Minimum dischage of storage nodes
subject to genmax { i in tgen } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= maxtprod[i];

#Maximum discharge of storage nodes
subject to sgenmin { i in storage } :
	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= storagemin[i];

#Demand of consumer nodes.
subject to sgenmax { i in storage } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= storagemax[i];
subject to loadfix {i in consumers} :
	sum { j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor, = loads[i];

#Balance supply and demand of energy. 
subject to prodloadeq :
	sum { i in (rgen union tgen union storage), j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = sum { i in consumers, j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor;

#go ahead and solve the equation/model
solve;
#display {i in nodes,j in nodes : capacity[i,j] <> 0}: i,j, ((theta[i] - theta[j])/ weight[i,j])*m_factor;
printf {i in storage,j in nodes : capacity [i,j] <> 0 } : "%d %d %.3f\n", i, j, ((theta[i] - theta[j])/ weight[i,j])*m_factor > "update.txt";
printf {i in nodes,j in nodes : capacity[i,j] <> 0} : "%d,%d,%.4f,%.4f,%.4f \n", i, j, ((theta[i] - theta[j])/ weight[i,j])*m_factor, (abs(((theta[i] - theta[j])/ weight[i,j])*m_factor)/capacity[i,j])*100, (abs(((theta[i] - theta[j])/ weight[i,j])*m_factor)/totload)*100 > "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";
printf {i in rgen} : "R, %d, %.4f\n", i, (rprodmax[i]-(sum{j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/ weight[i,j])*m_factor)) >> "sol" & outname & ".txt";
#printf {i in nodes,j in nodes : capacity[j,i] <> 0} : " flow in [ %d , %d ] = %.6f \n", j, i, ((theta[j] - theta[i])/ weight[j,i])*m_factor;
#printf {i in nodes} : "theta %d = %.6f\n", i, theta[i] >> "sol" & outname & ".txt"; 
end;



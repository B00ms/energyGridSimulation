param n_inner;
param n_cons;
param n_tgen;
param n_rgen;
param n_storage;
param n_tot;
param m_factor;
param pi;
param totload;
param c_curt;
param outname;

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

var theta {nodes} >= -pi/2, <= pi/2;

minimize obj :	(sum{i in tgen} ((sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/ weight[i,j] )*costs[i])) + 
		(sum{i in rgen} ((sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/ weight[i,j] )*rcost[i])) + 
		(sum{i in rgen} c_curt*(rprodmax[i]-(sum{j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j])));

subject to anglestability :
	theta[46], = 0; 

subject to flowcapmax { i in nodes,j in nodes : capacity[i,j] <> 0} :
	((theta[i]-theta[j])/weight[i,j])*m_factor, <= capacity[i,j];

subject to flowcapmin { i in nodes,j in nodes : capacity[i,j] <> 0} :
	((theta[i]-theta[j])/weight[i,j])*m_factor, >= -capacity[i,j];

subject to flowcons { i in inner } :
	sum{ j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = sum{ j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor;


subject to rgenmin { i in rgen } :
	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= rprodmin[i];

subject to rgenmax { i in rgen } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= rprodmax[i];


subject to genmin { i in tgen } :
	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= mintprod[i];

subject to genmax { i in tgen } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= maxtprod[i];

subject to sgenmin { i in storage } :
	sum { j in nodes : capacity[i,j] <> 0} (theta[i]-theta[j])/weight[i,j]*m_factor, >= storagemin[i];

subject to sgenmax { i in storage } :
	sum { j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, <= storagemax[i];
subject to loadfix {i in consumers} :
	sum { j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor, = loads[i];
 
subject to prodloadeq :
	sum { i in (rgen union tgen union storage), j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/weight[i,j])*m_factor, = sum { i in consumers, j in nodes : capacity[j,i] <> 0} ((theta[j]-theta[i])/weight[j,i])*m_factor;

solve;
#display {i in nodes,j in nodes : capacity[i,j] <> 0}: i,j, ((theta[i] - theta[j])/ weight[i,j])*m_factor;
printf {i in storage,j in nodes : capacity [i,j] <> 0 } : "%d %d %.3f\n", i, j, ((theta[i] - theta[j])/ weight[i,j])*m_factor > "update.txt";
printf {i in nodes,j in nodes : capacity[i,j] <> 0} : "%d,%d,%.4f,%.4f,%.4f \n", i, j, ((theta[i] - theta[j])/ weight[i,j])*m_factor, (abs(((theta[i] - theta[j])/ weight[i,j])*m_factor)/capacity[i,j])*100, (abs(((theta[i] - theta[j])/ weight[i,j])*m_factor)/totload)*100 > "sol" & outname & ".txt";
printf : "\n" >> "sol" & outname & ".txt";
printf {i in rgen} : "R, %d, %.4f\n", i, (rprodmax[i]-(sum{j in nodes : capacity[i,j] <> 0} ((theta[i]-theta[j])/ weight[i,j])*m_factor)) >> "sol" & outname & ".txt";
#printf {i in nodes,j in nodes : capacity[j,i] <> 0} : " flow in [ %d , %d ] = %.6f \n", j, i, ((theta[j] - theta[i])/ weight[j,i])*m_factor;
#printf {i in nodes} : "theta %d = %.6f\n", i, theta[i] >> "sol" & outname & ".txt"; 
end;



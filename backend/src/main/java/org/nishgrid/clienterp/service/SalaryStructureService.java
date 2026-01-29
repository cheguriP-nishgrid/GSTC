package org.nishgrid.clienterp.service;

import lombok.RequiredArgsConstructor;
import org.nishgrid.clienterp.dto.SalaryStructureRequest;
import org.nishgrid.clienterp.model.Employee;
import org.nishgrid.clienterp.model.SalaryStructure;
import org.nishgrid.clienterp.repository.EmployeeRepository;
import org.nishgrid.clienterp.repository.SalaryStructureRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SalaryStructureService {

    private final EmployeeRepository employeeRepository;
    private final SalaryStructureRepository salaryStructureRepository;

    public boolean createSalaryStructure(SalaryStructureRequest request) {
        Employee employee = employeeRepository.findByEmployeeCode(request.getEmployeeCode()).orElse(null);

        if (employee == null) {
            return false;
        }

        SalaryStructure salary = buildSalaryStructure(request, employee);
        salaryStructureRepository.save(salary);
        return true;
    }

    public List<SalaryStructure> getAllSalaryStructures() {
        return salaryStructureRepository.findAll();
    }
    public void save(SalaryStructure salaryStructure) {
        salaryStructureRepository.save(salaryStructure);
    }
    public SalaryStructure getById(Integer id) {
        return salaryStructureRepository.findById(id).orElse(null);
    }

    public boolean updateSalaryStructure(Integer id, SalaryStructureRequest request) {
        Optional<SalaryStructure> optional = salaryStructureRepository.findById(id);
        if (optional.isEmpty()) {
            return false;
        }

        SalaryStructure salary = optional.get();
        Employee employee = employeeRepository.findByEmployeeCode(request.getEmployeeCode()).orElse(null);
        if (employee == null) {
            return false;
        }

        SalaryStructure updated = buildSalaryStructure(request, employee);
        updated.setId(id);
        salaryStructureRepository.save(updated);
        return true;
    }

    public boolean deleteSalaryStructure(Integer id) {
        if (salaryStructureRepository.existsById(id)) {
            salaryStructureRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private SalaryStructure buildSalaryStructure(SalaryStructureRequest request, Employee employee) {
        SalaryStructure salary = new SalaryStructure();
        salary.setEmployee(employee);
        salary.setBasicSalary(request.getBasicSalary());
        salary.setHra(request.getHra());
        salary.setOtherAllowances(request.getOtherAllowances());
        salary.setPfDeduction(request.getPfDeduction());
        salary.setEsiDeduction(request.getEsiDeduction());
        salary.setTdsDeduction(request.getTdsDeduction());

        BigDecimal totalMonthly = request.getBasicSalary()
                .add(request.getHra())
                .add(request.getOtherAllowances())
                .subtract(request.getPfDeduction())
                .subtract(request.getEsiDeduction())
                .subtract(request.getTdsDeduction());

        salary.setTotalSalary(totalMonthly);
        salary.setYearSalary(totalMonthly.multiply(BigDecimal.valueOf(12)));

        return salary;
    }
}

package org.nishgrid.clienterp.service;

import lombok.RequiredArgsConstructor;
import org.nishgrid.clienterp.dto.PayslipRequest;
import org.nishgrid.clienterp.exception.DuplicateResourceException;
import org.nishgrid.clienterp.exception.ResourceNotFoundException;
import org.nishgrid.clienterp.model.Employee;
import org.nishgrid.clienterp.model.Payslip;
import org.nishgrid.clienterp.model.SalaryStructure;
import org.nishgrid.clienterp.repository.EmployeeRepository;
import org.nishgrid.clienterp.repository.PayslipRepository;
import org.nishgrid.clienterp.repository.SalaryStructureRepository; // Import the new repository
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PayslipService {

    private final EmployeeRepository employeeRepository;
    private final PayslipRepository payslipRepository;
    private final SalaryStructureRepository salaryStructureRepository; // Inject the new repository

    /**
     * This method is rewritten to use the real SalaryStructure.
     * The old simulation is removed.
     */
    public PayslipRequest calculatePayslipDetails(String employeeCode, String monthStr) {
        // Find the employee's salary structure
        SalaryStructure structure = salaryStructureRepository.findByEmployee_EmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Salary Structure not found for employee code: " + employeeCode));

        // --- Real Calculation Logic ---
        int totalWorkingDays = 22; // This can be made dynamic later
        int daysPresent = 20;      // This should come from an Attendance service

        // 1. Calculate Gross Salary from the structure
        BigDecimal grossSalary = structure.getBasicSalary()
                .add(structure.getHra())
                .add(structure.getOtherAllowances());
        grossSalary = grossSalary.setScale(2, RoundingMode.HALF_UP);

        // 2. Calculate Total Deductions from the structure
        BigDecimal fixedDeductions = structure.getPfDeduction()
                .add(structure.getEsiDeduction())
                .add(structure.getTdsDeduction());

        // 3. Calculate Leave Deduction based on days absent
        int daysAbsent = totalWorkingDays - daysPresent;
        BigDecimal perDaySalary = grossSalary.divide(BigDecimal.valueOf(totalWorkingDays), 2, RoundingMode.HALF_UP);
        BigDecimal leaveDeduction = perDaySalary.multiply(BigDecimal.valueOf(daysAbsent));

        BigDecimal totalDeductions = fixedDeductions.add(leaveDeduction).setScale(2, RoundingMode.HALF_UP);

        // 4. Calculate Net Salary
        BigDecimal netSalary = grossSalary.subtract(totalDeductions);

        // 5. Build the response DTO with real calculated data
        PayslipRequest calculatedData = new PayslipRequest();
        calculatedData.setEmployeeCode(employeeCode);
        calculatedData.setMonth(monthStr);
        calculatedData.setTotalWorkingDays(totalWorkingDays);
        calculatedData.setDaysPresent(daysPresent);
        calculatedData.setTotalSalary(grossSalary);
        calculatedData.setTotalDeductions(totalDeductions);
        calculatedData.setNetSalary(netSalary);

        return calculatedData;
    }

    // --- All other methods remain the same ---

    public String generatePayslip(PayslipRequest request) {
        Optional<Payslip> existingPayslip = payslipRepository.findByEmployee_EmployeeCodeAndMonth(request.getEmployeeCode(), request.getMonth());
        if (existingPayslip.isPresent()) {
            throw new DuplicateResourceException("It is already created for employee " + request.getEmployeeCode() + " for month " + request.getMonth());
        }

        Employee employee = employeeRepository.findByEmployeeCode(request.getEmployeeCode())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + request.getEmployeeCode()));

        Payslip payslip = new Payslip();
        payslip.setEmployee(employee);
        payslip.setMonth(request.getMonth());
        payslip.setTotalWorkingDays(request.getTotalWorkingDays());
        payslip.setDaysPresent(request.getDaysPresent());
        payslip.setTotalSalary(request.getTotalSalary());
        payslip.setTotalDeductions(request.getTotalDeductions());
        payslip.setNetSalary(request.getNetSalary());
        payslip.setGeneratedOn(LocalDate.now());
        payslip.setPaymentStatus("Unpaid");

        payslipRepository.save(payslip);
        return "Payslip generated for employee: " + employee.getFullName();
    }

    public List<Payslip> getAllPayslips() { return payslipRepository.findAll(); }
    public List<Payslip> getPayslipsByEmployee(String employeeCode) { return payslipRepository.findByEmployee_EmployeeCode(employeeCode); }
    public Payslip getPayslipByEmployeeAndMonth(String employeeCode, String month) { return payslipRepository.findByEmployee_EmployeeCodeAndMonth(employeeCode, month).orElse(null); }
    public List<Payslip> getPayslipsByPaymentStatus(String status) { return payslipRepository.findByPaymentStatus(status); }
    public List<Payslip> getPayslipsByMonth(String month) { return payslipRepository.findByMonth(month); }
    public String updatePaymentStatus(Integer payslipId, String status) {
        Payslip payslip = payslipRepository.findById(payslipId).orElseThrow(() -> new ResourceNotFoundException("Payslip not found with ID: " + payslipId));
        payslip.setPaymentStatus(status);
        payslipRepository.save(payslip);
        return "Payment status updated to " + status + " for payslip ID: " + payslipId;
    }
}